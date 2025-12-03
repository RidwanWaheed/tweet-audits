# Architecture and Design Tradeoffs

**Project:** Tweet Audit
**Author:** Ridwan
**Date:** January 2025

This doc explains the key technical decisions I made building this tweet audit tool, and more importantly, *why* I made them. Some decisions were obvious, others took production debugging to get right.

---

## 1. Processing Architecture

I went with **batch processing** (you run it manually when you need it) instead of a long-running streaming service.

### Comparison

| Factor | Batch (Chosen) | Streaming Service |
|--------|----------------|-------------------|
| Execution Model | Manual, user-initiated | Continuous, automatic |
| Complexity | Low | High |
| Resource Usage | Only during execution | Constant |
| Infrastructure | Simple JAR execution | Requires server/container |
| User Control | Explicit | Implicit |
| Debugging | Easier | More complex |

### Why batch?

This is a learning project, and I wanted to focus on core Java/Spring patterns rather than infrastructure management. Batch processing also makes sense for the use case — analyzing Twitter archives is typically a one-time or occasional task, not something that needs 24/7 monitoring.

I used a similar pattern in my Python thesis project (LLM classification with Ollama), and it worked well. The user has explicit control over when processing happens, which makes debugging easier and avoids the complexity of managing a server.

A streaming service would be the right call for multi-user SaaS platforms, real-time monitoring, or enterprise environments with existing infrastructure.

---

## 2. Rate Limiting Strategy

I'm using **Thread.sleep()** with manual quota tracking instead of fancy rate limiter libraries.

### Comparison

| Approach | Handles RPM | Handles RPD | Complexity | Dependencies |
|----------|-------------|-------------|------------|--------------|
| Thread.sleep() (Chosen) | Yes | Manual | Low | None |
| Guava RateLimiter | Yes | No | Medium | Guava |
| @Scheduled + Semaphore | Yes | Manual | High | Spring Context |

### Why Thread.sleep()?

Here's the thing: Gemini has *two* rate limits:
- **15 RPM** (requests per minute) — easy to handle
- **1000 RPD** (requests per day) — the hard part

The RPM limit is straightforward: wait 4 seconds between requests and you're good. Thread.sleep() solves this perfectly.

The RPD limit is where it gets interesting. No rate limiter library handles daily quotas that reset at a specific timezone (midnight Pacific Time). That requires persistent state tracking no matter what approach you use.

So I went with Thread.sleep() because:
- It solves the actual constraint (15 RPM)
- Zero dependencies
- The code is explicit and easy to understand
- All the fancier approaches still need manual daily quota tracking anyway

Adding Guava RateLimiter or Spring @Scheduled would add complexity without solving the hard problem.

---

## 2.1. Dynamic (Adaptive) Rate Limiting

The rate limiter adjusts delays based on how the API is actually behaving, rather than using fixed delays.

### Strategy

Here's how it adapts:
- **Fast responses (< 500ms):** Reduce delay by 100ms (gradually speed up)
- **Normal responses (500-3000ms):** Keep current delay
- **Slow responses (> 3000ms):** Increase delay by 500ms (give the API breathing room)
- **Rate limit hit (429):** Double delay (aggressive backoff)
- **Server error (5xx):** Increase delay by 500ms (moderate backoff)

Delays stay between 200ms (minimum) and 10s (maximum), starting at 1000ms.

### Comparison

| Approach | Adapts to API | Handles Bursts | Complexity | Efficiency |
|----------|---------------|----------------|------------|------------|
| **Adaptive (Chosen)** | Yes | Yes | Low | High |
| Fixed Delays | No | No | Very Low | Low |
| Exponential Backoff Only | Partial | Yes | Very Low | Medium |

### Why adaptive?

Fixed delays waste time when the API is responding quickly. Exponential backoff only increases delays, never speeds up.

With adaptive rate limiting, the code automatically finds the sweet spot — processing as fast as possible without overwhelming the API. If Gemini starts responding slowly, we back off. If it's consistently fast, we speed up.

---

## 2.2. Daily Quota Tracking

I track daily quota usage in a persistent JSON file that's timezone-aware (Pacific Time).

### How it works

The system:
- Saves quota state to `results/daily_quota.json` (survives app restarts)
- Tracks quota based on Pacific Time (Gemini resets at midnight PST)
- Automatically detects new days and resets the counter
- Checks remaining quota before processing starts
- Warns at 90% usage (855/950 requests)
- Stops gracefully before hitting the hard limit

**Quota file format:**
```json
{
  "currentDate": "2025-01-26",
  "requestCount": 145
}
```

### Comparison

| Approach | Persistent | Timezone-Aware | Complexity | Dependencies |
|----------|-----------|----------------|------------|--------------|
| **JSON Tracking (Chosen)** | Yes | Yes | Low | Jackson (existing) |
| In-Memory Tracking | No | N/A | Very Low | None |
| Database Tracking | Yes | Yes | High | JDBC/JPA |
| No Tracking | N/A | N/A | None | None |

### Why track daily quota?

Without tracking, you might process 100 tweets, hit quota at tweet #50, and watch the rest fail. That's frustrating.

The 1000 requests per day limit is the real constraint for very large archives. At 15 RPM, processing the full 1000-request daily quota takes just over an hour. However, if you have 10,000+ tweets to audit, you're looking at multi-day jobs spanning the quota reset cycle. Quota tracking isn't optional — it's essential.

I chose JSON because:
- It persists across restarts (unlike in-memory)
- Simple implementation using Jackson (already in the project)
- Timezone-aware (matches Gemini's midnight PST reset)
- Human-readable for debugging

A database would be overkill for a single-user batch tool.

**Example output:**
```
=== Daily Quota Status ===
  Date: 2025-01-26
  Used: 580/1000 requests (58%)
  Remaining: 370 requests
  Resets: midnight PST (in 8 hours)
```

---

## 2.3. Graceful Shutdown on Quota Exhaustion

When quota is exhausted, the app shuts down gracefully using a protected method that can be mocked in tests.

### Comparison

| Approach | Testable | Complexity | Flexibility |
|----------|----------|------------|-------------|
| Direct System.exit() | No | Very Low | None |
| Environment checks | Yes | Low | Low |
| **Protected method (Chosen)** | **Yes** | **Low** | **Medium** |
| DI with ShutdownHandler | Yes | Medium | High |

### Why a protected method?

I needed the shutdown to be testable. Calling `System.exit()` directly would kill the test suite, so I extracted it into a protected method that Mockito can spy on and override.

This is simple (just one method, no interfaces) and sufficient for the current use case. If I needed multiple shutdown scenarios later, I could refactor to dependency injection, but YAGNI applies here.

One tricky bit I discovered: `SpringApplication.exit()` triggers Spring's cleanup (close connections, flush logs, etc.) but doesn't actually terminate the JVM. You need both `SpringApplication.exit()` (cleanup) and `System.exit()` (termination).

---

## 2.4. Quota Safety Threshold

I stop processing at 95% of the daily limit (950/1000 requests) instead of pushing to the full 1000.

### The problem I ran into

During production testing, I hit an interesting issue:
- My client-side tracker: 997/1000 requests (3 remaining)
- Gemini's response: **429 Rate Limit Exceeded**

According to my tracking, I had 3 requests left. But Gemini disagreed.

Turns out, there are timing issues in distributed systems: clock drift between my machine and Gemini's servers, race conditions in quota increment timing, retry logic discrepancies, etc. My tracker and Gemini's tracker weren't perfectly in sync.

### Approach Comparison

| Threshold | Buffer | Quota Waste | Risk Level |
|-----------|--------|-------------|------------|
| 1000 (100%) | 0 requests | None | High - proven to fail |
| 990 (99%) | 10 requests | Minimal | Medium |
| **950 (95%)** | **50 requests** | **5%** | **Low** |
| 900 (90%) | 100 requests | 10% | Very Low |

### Why 95%?

I looked at what major API providers recommend and they all use 5-10% safety margins:
- **AWS Lambda**: 90% threshold (900/1000 concurrent executions)
- **Stripe API**: 80-90% throttling
- **GitHub API**: 90% threshold (4500/5000 req/hour)
- **Google Cloud**: Monitor at 80-90%, alert at 95%

The common pattern is: warn at 80-90%, hard stop at 95%.

So I went with 95% (950 requests). This gives me a 50-request buffer — way more than the 3-request gap I observed, providing a 16.6x safety margin. It wastes 5% of quota, but prevents hitting unexpected 429 errors.

The threshold is configurable via `quota.safety-threshold` if I need to adjust it later.

---

## 3. State Management & Checkpointing

I use two separate files: a JSON checkpoint for runtime state and a CSV for final output.

### Structure

**Checkpoint (results/checkpoint.json):**
```json
{
  "last_processed_tweet_id": "123",
  "processed_tweet_ids": ["1", "2", "3"],
  "timestamp": "2025-11-26T06:00:00Z",
  "total_processed": 3,
  "total_tweets": 10,
  "flagged_count": 1,
  "error_count": 0
}
```

**Output (results/flagged_tweets.csv):**
```csv
tweetUrl,tweetId,status,matchedCriteria,reason
```

### Comparison

| Factor | JSON + CSV (Chosen) | Single CSV | Database |
|--------|---------------------|------------|----------|
| Resume Speed | Fast (Set lookup) | Slow (scan file) | Fast (indexed) |
| Human Readable | Both formats | Yes | No (requires client) |
| Dependencies | Zero | Zero | DB server required |
| Atomicity | File-level | Row-level | Transaction-level |
| Portability | Maximum | High | Low |
| Complexity | Low | Lowest | High |

### Why separate files?

The checkpoint and output serve different purposes:
- **Checkpoint** = runtime state (which tweets have been processed)
- **CSV** = final output (which tweets were flagged or errored)

Using a single CSV would mean storing ALL tweets (flagged + clean) just to track what's been processed. That mixes runtime state with final output, and resuming would require scanning the entire CSV file to find processed IDs.

With separate files:
- Resume is fast (O(1) Set lookup instead of O(n) file scan)
- CSV only contains flagged tweets and errors (clean, user-facing)
- Both files are text-based and human-readable
- Zero dependencies (no database setup)
- Checkpoint is saved after each batch (max loss: 1 batch if app crashes)

A database would be overkill for a single-user CLI tool, and this is a learning project focused on Java/Spring patterns, not database management.

---

## 3.1. Incremental CSV Writing

I write flagged tweets to the CSV after each batch and clear the results list between batches.

### Comparison

| Approach | Crash Safety | Memory Usage | I/O Ops | Data Loss Risk |
|----------|--------------|--------------|---------|----------------|
| **Per-batch (Chosen)** | **High** | **Low** | **Medium** | **Max 1 batch** |
| Final write only | None | High | Minimal | Entire run |
| Per-tweet | Maximum | Minimal | High | 1 tweet |

### Why per-batch?

If the app crashes or quota runs out mid-processing, incremental writing means all the work up to the last completed batch is saved. Without this, you'd lose everything.

This also keeps memory usage low — I clear the results list after each batch write to avoid accumulation.

### A critical bug I fixed

Initially, I forgot to clear the results list after writing. This caused exponential duplicates:
- Batch 1: Writes 15 tweets (15 total)
- Batch 2: Writes 30 tweets (batch 1 + batch 2 = 15 duplicates!)
- Batch 3: Writes 45 tweets (batch 1+2+3 = 30 duplicates!)

The fix is simple but essential:
```java
csvWriter.appendResults(results);  // Write batch to CSV
results.clear();                   // Clear to prevent accumulation
```

Now each tweet is written exactly once.

Writing at the end only would lose all data on crashes. Writing per-tweet would be 10x more I/O operations with minimal benefit.

---

## 4. Concurrency Model

I process tweets sequentially (one at a time) instead of using parallelism or async processing.

### Comparison

| Model | Throughput | Memory | API Load | Rate Limit Compliance |
|-------|-----------|--------|----------|----------------------|
| Sequential (Chosen) | 15/min | Minimal | Smooth | Perfect |
| Batched (N=15) | 15/min* | Medium | Bursty | Violates instantaneous limit |
| Fully Async | 15/min* | High | Overwhelming | Complete violation |

*Limited by API rate limit, not concurrency model

### Why sequential?

Here's the key insight: Gemini's API limits are 15 requests per minute, not concurrent connections.

If I used parallel processing, I'd send 15 requests instantly and immediately violate the rate limit. All three approaches achieve the same throughput anyway — the bottleneck is the API, not the code.

Sequential processing is:
- The only approach that respects "15 requests per minute" without bursting
- The simplest implementation with the lowest risk
- Deterministic, which makes debugging easier
- Safe from overwhelming the API or triggering bans

Parallelism would be appropriate for internal APIs with no rate limits, or operations where latency compounds. But when an external API has strict rate limits, concurrency provides no benefit and adds significant risk.

---

## 5. Checkpoint Frequency

I save the checkpoint after every batch (15 tweets), not after every tweet.

### Comparison

| Strategy | Max Loss on Crash | Quota Waste | Disk I/O | Processing Overhead |
|----------|-------------------|-------------|----------|---------------------|
| Every Batch (Chosen) | 15 tweets | Up to 15 requests | ~1ms per batch | 0.02% |
| Every Tweet | 1 tweet | ~1 request | ~1ms per tweet | 0.3% |
| Manual Only | All progress | Unlimited | 0 | 0% |

### Why per-batch?

The math is compelling:
- JSON write: ~1ms
- Gemini API call: ~2,000ms per tweet
- Batch size: 15 tweets
- Total batch time: ~30,000ms (30 seconds)
- Checkpoint overhead: 1ms / 30,000ms = 0.003%

For 100 tweets, checkpointing adds about 0.1 seconds to a ~200-second run. That's negligible.

I'm already pausing 60 seconds between batches for rate limiting, so saving during that pause is a natural checkpoint point. The code is simpler this way — no need to save inside the tweet processing loop.

The max risk is losing 15 tweets if the app crashes mid-batch, which is acceptable on the free tier ($0 cost).

**Crash scenarios:**
```
Scenario 1: Crash during batch processing
  - Saved state: Last completed batch
  - Lost progress: Current batch (max 15 tweets)
  - Recovery: Resume from next batch

Scenario 2: Crash during rate limit pause
  - Saved state: Just completed batch
  - Lost progress: 0 tweets
  - Recovery: Resume from next batch immediately
```

Saving every tweet would be 15x more disk writes with minimal benefit (API call time dominates anyway). Manual checkpoints only would defeat the purpose — any crash would lose ALL progress.

---

## 6. Checkpoint Cleanup Strategy

The checkpoint is automatically deleted on successful completion, but preserved on crashes.

### Comparison

| Strategy | User Control | Crash Recovery | Re-run Behavior | Disk Usage |
|----------|--------------|----------------|-----------------|------------|
| Auto-delete on success (Chosen) | Full control on crash | Resume | Always fresh after success | Auto-managed |
| Auto-delete always | None | No resume | Always fresh | Auto-managed |
| Never delete | Full | Always resume | Must delete manually | Grows unbounded |

### Implementation

**On successful completion:**
```java
checkpointManager.deleteCheckpoint();  // Clean slate for next run
log.info("Checkpoint deleted (processing complete)");
```

**On crash/interruption:**
- Checkpoint file remains
- User decides: Resume (run again) or Start fresh (delete checkpoint manually)

### Why auto-delete on success?

When processing completes successfully, I delete the checkpoint automatically. This prevents:
- Accidentally resuming from old state on the next run
- Stale checkpoints from previous runs
- User confusion ("Why is it resuming when I already processed everything?")

But if the app crashes or hits quota mid-run, the checkpoint is preserved so you can:
- Resume after fixing issues (API key, network, etc.)
- Inspect progress ("How far did I get?")
- Make manual retry decisions ("Start fresh or continue?")

This gives the user full control on crashes while providing a clean slate after successful runs.

### User Experience

**Successful run:**
```bash
$ mvn spring-boot:run
# Processes all 100 tweets
# Checkpoint deleted automatically
# ✓ Next run starts fresh
```

**Interrupted run:**
```bash
$ mvn spring-boot:run
# Processes 30 tweets, then Ctrl+C
# Checkpoint preserved (30 tweets)

# Resume:
$ mvn spring-boot:run
# Resumes from tweet 31

# OR start fresh:
$ rm results/checkpoint.json
$ mvn spring-boot:run
# Processes from tweet 1
```

---

## 7. Error Recovery

I use Spring's @Retryable with exponential backoff for transient failures, plus checkpoint-based error marking for persistent issues.

### Configuration

- Max attempts: 3
- Initial delay: 2 seconds
- Multiplier: 2.0 (exponential)
- Jitter: Enabled (random variance)
- Retry: 5xx errors, timeouts, 429
- No retry: 4xx client errors (except 429)

### Comparison

| Approach | Transient Handling | Code Quality | Configurability | Testing |
|----------|-------------------|--------------|-----------------|---------|
| @Retryable (Chosen) | Excellent | Declarative | Easy | Simple |
| Manual Retry | Good | Imperative | Requires code changes | Complex |
| Fail-Fast | None | Simple | N/A | Trivial |

### Why @Retryable?

@Retryable is the industry-standard pattern for external API calls. It handles transient failures (network hiccups, temporary outages) with exponential backoff and jitter to avoid overwhelming the service.

The declarative approach keeps retry logic separate from business logic, and it only retries appropriate errors:
- **Retryable:** Network timeouts, 5xx server errors, 429 rate limits (transient)
- **Non-retryable:** 400 Bad Request, 401 Unauthorized, 404 Not Found (permanent)

### Two-tier error handling

Here's where it gets interesting: @Retryable and checkpoint error marking work at **different time scales**.

**@Retryable (seconds):** Automatic recovery from transient issues
- Network hiccup → Retry after 1s → Success
- Brief server outage → Retry after 2s → Success
- User never sees these errors

**Checkpoint marking (across runs):** Manual recovery from persistent issues
- Invalid API key → All 3 retries fail → Mark as ERROR → User fixes key → Manual retry
- Malformed data → All 3 retries fail → Mark as ERROR → User inspects tweet
- Prevents infinite retry loops on permanently failing tweets

After @Retryable exhausts all attempts, I mark the tweet as processed:
```java
processedTweetIds.add(tweet.getIdStr());  // Don't retry this tweet automatically
errorCount++;
```

Without this, the same failing tweet would be retried on every run (infinite loop). With this, the user controls retry via checkpoint deletion (manual decision).

**Recovery flow:**
1. @Retryable tries 3 times (automatic, seconds)
2. Still fails → Mark as ERROR in checkpoint (save progress)
3. Continue processing (don't get stuck)
4. User sees ERROR in CSV → Fixes root cause → Deletes checkpoint → Retries manually

---

## 8. Timezone Handling

I track quota based on Pacific Time (America/Los_Angeles), not my local time (Berlin).

### The Problem

Gemini's quota resets at midnight Pacific Time, but I'm developing in Berlin, Germany (PT + 8/9 hours). If I used local time, my quota tracker and Gemini's quota tracker would be desynchronized.

### Scenario Comparison

**Pacific Time (Chosen):**
```
8:50 AM Berlin = 11:50 PM PT (Jan 14)
Code checks: quota for Jan 14
Gemini enforces: quota for Jan 14
Result: Synchronized ✓

9:00 AM Berlin = 12:00 AM PT (Jan 15)
Code checks: quota for Jan 15 (reset)
Gemini enforces: quota for Jan 15 (reset)
Result: Synchronized ✓
```

**Local Time (Berlin):**
```
8:50 AM Berlin = 11:50 PM PT
Code thinks: Jan 15 (local date)
Gemini thinks: Jan 14 (Pacific date)
Result: Desynchronized - unnecessary 429 errors

9:00 AM Berlin = Midnight PT (Gemini resets)
Code thinks: Still Jan 15 (same day)
Gemini thinks: Now Jan 15 (new quota available)
Result: Misses quota reset window
```

### Why Pacific Time?

Using Pacific Time means my quota tracking matches Gemini's authoritative reset schedule, regardless of where I'm developing or deploying. Java's `ZoneId` handles DST automatically, which prevents an entire class of timezone-related bugs.

Core principle: In distributed systems, always use the authoritative service's timezone for quota/rate limit tracking.

---

## Summary

| Decision | Chosen Approach | Why |
|----------|----------------|-----|
| Architecture | Batch processing | Simplicity, learning focus, proven pattern |
| Rate Limiting | Thread.sleep() | Solves the actual constraint, zero dependencies |
| Adaptive Rate Limiting | Response-based delay adjustment | Optimizes throughput, respectful API behavior |
| Daily Quota Tracking | Persistent JSON (Pacific Time) | Prevents quota exhaustion, timezone-correct |
| Quota Safety Threshold | Configurable 95% threshold (950/1000) | Prevents 429s from clock drift/race conditions |
| Graceful Shutdown | Protected method extraction | Testable, simple, avoids over-engineering |
| State Management | JSON + CSV | Fast resume, clean separation of concerns |
| Concurrency | Sequential | Only valid approach for rate-limited APIs |
| Checkpoint Frequency | Per-batch (15 tweets) | Aligns with rate limiting, negligible overhead |
| Checkpoint Cleanup | Auto-delete on success | Clean slate for next run, manual resume on crash |
| Error Recovery | @Retryable | Industry standard, handles transient failures |
| Timezone | Pacific Time | Matches Gemini's authoritative quota source |

---

## Key Learnings

Production debugging taught me:

1. **Client-side quota tracking needs safety margins** — Hit 997/1000 → got 429 anyway due to clock drift. Industry standard is 90-95% thresholds, not 100%.

2. **SpringApplication.exit() ≠ System.exit()** — Spring's cleanup method returns an exit code but doesn't actually terminate the JVM. You need both.

3. **Always clear lists after batch writes** — Forgot this once and got exponential CSV duplicates (batch 1: 15 tweets, batch 2: 30 tweets, batch 3: 45 tweets...).

4. **Timezone matters for quota tracking** — Gemini resets at midnight Pacific Time. Using Berlin time would cause off-by-one-day errors.

5. **Rate limits are multi-dimensional** — RPM is easy (Thread.sleep()), but RPD requires persistent state tracking no matter which library you use.

---

## References

- AWS: [Timeouts, Retries, and Backoff with Jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [HTTP Connection Management (MDN)](https://developer.mozilla.org/en-US/docs/Web/HTTP/Connection_management_in_HTTP_1.x)
- [Google Gemini API Documentation](https://ai.google.dev/gemini-api/docs/quota)
- [Jackson ObjectMapper Documentation](https://fasterxml.github.io/jackson-databind/javadoc/2.9/com/fasterxml/jackson/databind/ObjectMapper.html)
- [Spring Retry Reference](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- Personal reference: Python thesis project (LLM classification with Ollama)