# Architecture and Design Tradeoffs

**Project:** Tweet Audit  
**Author:** Ridwan  
**Date:** January 2025  
**Purpose:** Document key architectural decisions and their tradeoffs

---

## 1. Processing Architecture

**Decision:** Batch Processing  
**Alternatives Considered:** Long-running streaming service with @Scheduled

### Comparison

| Factor | Batch (Chosen) | Streaming Service |
|--------|----------------|-------------------|
| Execution Model | Manual, user-initiated | Continuous, automatic |
| Complexity | Low | High |
| Resource Usage | Only during execution | Constant |
| Infrastructure | Simple JAR execution | Requires server/container |
| User Control | Explicit | Implicit |
| Debugging | Easier | More complex |

### Rationale

Batch processing chosen for:
- Alignment with learning objectives (focus on core patterns, not infrastructure)
- Simpler implementation matches project scope
- User has control over processing timing
- Similar pattern proven successful in Python thesis project
- No need for 24/7 server infrastructure
- Natural fit for one-time archive analysis

Streaming appropriate for: Multi-user SaaS, real-time monitoring, enterprise environments with existing infrastructure.

---

## 2. Rate Limiting Strategy

**Decision:** Thread.sleep() with manual quota tracking  
**Alternatives Considered:** Guava RateLimiter, Spring @Scheduled + Semaphore

### Comparison

| Approach | Handles RPM | Handles RPD | Complexity | Dependencies |
|----------|-------------|-------------|------------|--------------|
| Thread.sleep() (Chosen) | Yes | Manual | Low | None |
| Guava RateLimiter | Yes | No | Medium | Guava |
| @Scheduled + Semaphore | Yes | Manual | High | Spring Context |

### Rationale

**Key insight:** Gemini has two rate limits:
- 10 RPM (requests per minute) - easy to handle with simple wait
- 250 RPD (requests per day) - requires persistent state tracking regardless of rate limiter

Thread.sleep() chosen because:
- Solves the actual problem (10 RPM = wait 6 seconds between requests)
- Zero dependencies
- Explicit and understandable
- All approaches require manual RPD tracking via CSV anyway
- Guava RateLimiter doesn't solve daily quota problem
- @Scheduled adds unnecessary complexity for batch architecture

**Critical distinction:** None of the rate limiters address the hard problem (250 RPD with timezone-aware reset). That requires CSV state tracking in all cases.

---

## 2.1. Dynamic (Adaptive) Rate Limiting

**Decision:** Adaptive delay adjustment based on API response behavior
**Alternatives Considered:** Fixed delays, exponential backoff only

### Strategy

**Adaptive behavior:**
- **Fast responses (< 500ms):** Reduce delay by 100ms (speed up gradually)
- **Normal responses (500-3000ms):** Maintain current delay
- **Slow responses (> 3000ms):** Increase delay by 500ms (be more respectful)
- **Rate limit hit (429):** Double delay (aggressive backoff)
- **Server error (5xx):** Increase delay by 500ms (moderate backoff)

**Delay bounds:** 200ms (minimum) to 10s (maximum)
**Initial delay:** 1000ms (conservative start)

### Comparison

| Approach | Adapts to API | Handles Bursts | Complexity | Efficiency |
|----------|---------------|----------------|------------|------------|
| **Adaptive (Chosen)** | Yes | Yes | Low | High |
| Fixed Delays | No | No | Very Low | Low |
| Exponential Backoff Only | Partial | Yes | Very Low | Medium |

### Rationale

Adaptive rate limiting chosen for:
- **Automatic optimization:** Speeds up when API is fast, slows down when API is stressed
- **Respectful behavior:** Backs off on rate limits/errors without manual intervention
- **Better throughput:** Can process faster than fixed 6-second delays when API is responsive
- **Simplicity:** Single component with clear responsibilities
- **Testability:** Easy to unit test different scenarios

**How it works in practice:**
1. Start conservative (1000ms delay)
2. If API responds quickly repeatedly → gradually reduce to 200ms minimum
3. If API slows down or errors → increase delay to reduce load
4. If rate limited → aggressively back off (double delay)

**Why not fixed delays:**
- Wastes time when API is fast (fixed 6s delay might be too conservative)
- Doesn't adapt to API stress (keeps hammering even when API is slow)

**Why not exponential backoff only:**
- Only increases delays, never speeds up
- Doesn't distinguish between fast/normal/slow responses
- Misses optimization opportunities

### Implementation

See: `AdaptiveRateLimiter.java` with 12 comprehensive unit tests covering:
- Speed up/slow down scenarios
- Boundary conditions (min/max delays)
- Rate limit and server error handling
- Multi-scenario adaptation

---

## 3. State Management & Checkpointing

**Decision:** JSON checkpoint + CSV output
**Alternatives Considered:** Single CSV for both, database

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

### Rationale

JSON checkpoint + CSV output chosen for:
- **Separation of concerns**: Checkpoint = runtime state, CSV = final output
- **Fast resume**: Set-based lookup O(1) vs CSV scan O(n)
- **Clean output**: CSV only contains flagged tweets + errors (user-facing)
- **Zero dependencies**: No database setup required
- **Manual cleanup strategy**: User controls when to start fresh (delete checkpoint)
- **Crash safety**: Checkpoint saved after each batch (max loss: 1 batch)
- **Human inspectable**: Both files are text-based and readable

**Why not single CSV?**
- Would need to store ALL tweets (flagged + clean) to track processed IDs
- Resume requires scanning entire file to find processed IDs
- Mixes runtime state with final output
- CSV doesn't support Sets (would need pipe-separated string)

**Why not database?**
- Overkill for single-user CLI tool
- Adds deployment complexity
- Not portable (can't easily share/inspect)
- Learning project focuses on Java/Spring, not DB management

---

## 4. Concurrency Model

**Decision:** Sequential processing  
**Alternatives Considered:** Batched parallel (N=10), Fully async

### Comparison

| Model | Throughput | Memory | API Load | Rate Limit Compliance |
|-------|-----------|--------|----------|----------------------|
| Sequential (Chosen) | 10/min | Minimal | Smooth | Perfect |
| Batched (N=10) | 10/min* | Medium | Bursty | Violates instantaneous limit |
| Fully Async | 10/min* | High | Overwhelming | Complete violation |

*Limited by API rate limit, not concurrency model

### Rationale

**Critical constraint:** API limits 10 requests per minute (RPM), not concurrent connections.

Sequential processing chosen because:
- Only approach that respects "10 requests per minute" without bursting
- Parallel execution would send 10 requests instantly, violating rate limit
- All approaches achieve same throughput (limited by API, not code)
- Simplest implementation with lowest risk
- Deterministic behavior aids debugging
- No risk of overwhelming API or triggering bans

**Key insight:** When external API has strict rate limits, concurrency provides no benefit and adds significant risk.

Parallel appropriate for: Internal APIs, no rate limits, operations where latency compounds.

---

## 5. Checkpoint Frequency

**Decision:** Save after every batch (10 tweets)
**Alternatives Considered:** After every tweet, manual checkpoints only

### Comparison

| Strategy | Max Loss on Crash | Quota Waste | Disk I/O | Processing Overhead |
|----------|-------------------|-------------|----------|---------------------|
| Every Batch (Chosen) | 10 tweets | Up to 10 requests | ~1ms per batch | 0.03% |
| Every Tweet | 1 tweet | ~1 request | ~1ms per tweet | 0.3% |
| Manual Only | All progress | Unlimited | 0 | 0% |

### Performance Analysis

**Current implementation:**
- JSON write: ~1ms
- Gemini API call: ~2,000ms per tweet
- Batch size: 10 tweets
- Total batch time: ~20,000ms (20 seconds)
- Checkpoint overhead: 1ms / 20,000ms = 0.0005%

**Actual timing:**
```
Batch 1 (10 tweets): 20 seconds processing + 1ms save = 20.001s
Batch 2 (10 tweets): 20 seconds processing + 1ms save = 20.001s
Total for 100 tweets: ~200 seconds (checkpoint adds ~0.1s)
```

### Rationale

Batch checkpointing chosen for:
- **Aligns with rate limiting**: Already pausing 60s between batches
- **Negligible overhead**: 1ms checkpoint vs 20s batch processing
- **Acceptable risk**: Max loss is 10 tweets (100 API requests = $0 on free tier)
- **Natural checkpoint points**: Save during rate limit pause
- **Simpler code**: No need to save inside tweet processing loop

**Why not every tweet?**
- 10x more disk writes (100 vs 10 for 100 tweets)
- Adds complexity to inner loop
- API call time (2s) dominates anyway
- Risk difference minimal (1 vs 10 tweets lost)

**Why not manual only?**
- Defeats the purpose of checkpointing
- Any crash loses ALL progress
- Unacceptable for large archives (1000+ tweets = hours of work)

**Crash scenarios:**
```
Scenario 1: Crash during batch processing
  - Saved state: Last completed batch
  - Lost progress: Current batch (max 10 tweets)
  - Recovery: Resume from next batch

Scenario 2: Crash during rate limit pause
  - Saved state: Just completed batch
  - Lost progress: 0 tweets
  - Recovery: Resume from next batch immediately
```

---

## 6. Checkpoint Cleanup Strategy

**Decision:** Manual cleanup (user decides)
**Alternatives Considered:** Auto-delete on success, auto-delete on failure, never delete

### Comparison

| Strategy | User Control | Crash Recovery | Re-run Behavior | Disk Usage |
|----------|--------------|----------------|-----------------|------------|
| Manual (Chosen) | Full control | Resume or fresh | User chooses | Requires cleanup |
| Auto-delete on success | Limited | Resume | Always fresh | Auto-managed |
| Auto-delete on failure | None | No resume | Always fresh | Auto-managed |
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

### Rationale

Manual cleanup chosen for:
- **User flexibility**: User controls when to resume vs start fresh
- **Debugging capability**: Can inspect checkpoint after completion
- **Safe default**: Preserves state on crash (enables resume)
- **Explicit behavior**: User knows what will happen (resume or fresh)

**Deletion on success prevents:**
- Accidentally resuming from old state
- Stale checkpoints from previous runs
- User confusion ("Why is it resuming?")

**Preserving on failure enables:**
- Resume after fixing issues (API key, network, etc.)
- Inspect progress ("How far did I get?")
- Manual retry decisions ("Start fresh or continue?")

**Alternative approaches rejected:**

**Auto-delete on success only:** (Also considered, very close)
- Pro: Clean slate for next run
- Pro: No stale checkpoints
- Con: Can't inspect final state
- **Verdict:** Acceptable alternative, chosen for simplicity

**Auto-delete on failure:**
- Con: Loses all progress on crash
- Con: Forces starting over
- **Verdict:** Defeats purpose of checkpointing

**Never delete:**
- Con: Stale checkpoints cause confusion
- Con: Requires manual cleanup every time
- Con: First run after completion would resume (wrong!)
- **Verdict:** Poor user experience

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

**Decision:** Spring @Retryable with exponential backoff  
**Alternatives Considered:** Manual retry loops, fail-fast

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

### Rationale

@Retryable chosen for:
- Industry-standard pattern for external API calls
- Handles transient failures (network issues, temporary outages)
- Exponential backoff reduces load on struggling services
- Jitter prevents thundering herd on simultaneous failures
- Declarative approach separates retry logic from business logic
- Exception-specific retry (only retry appropriate errors)
- Graceful degradation via @Recover method

**Error classification:**
- Retryable: Network timeouts, 5xx server errors, 429 rate limits (transient)
- Non-retryable: 400 Bad Request, 401 Unauthorized, 404 Not Found (permanent)

### Two-Tier Error Handling Strategy

**Critical distinction:** @Retryable and checkpoint error marking work at **different time scales**.

**@Retryable (seconds):** Automatic recovery from transient issues
- Network hiccup → Retry after 1s → Success
- Brief server outage → Retry after 2s → Success
- User never sees these errors

**Checkpoint marking (across runs):** Manual recovery from persistent issues
- Invalid API key → All 3 retries fail → Mark as ERROR → User fixes key → Manual retry
- Malformed data → All 3 retries fail → Mark as ERROR → User inspects tweet
- Prevents infinite retry loops on permanently failing tweets

**Why mark failed tweets as "processed":**
```java
// After @Retryable exhausts all attempts:
processedTweetIds.add(tweet.getIdStr());  // Don't retry this tweet automatically
errorCount++;
```

Without this: Same failing tweet retried on every run → infinite loop
With this: User controls retry via checkpoint deletion (manual decision)

**Recovery flow:**
1. @Retryable tries 3 times (automatic, seconds)
2. Still fails → Mark as ERROR in checkpoint (save progress)
3. Continue processing (don't get stuck)
4. User sees ERROR in CSV → Fixes root cause → Deletes checkpoint → Retries manually

Manual retry appropriate for: Non-Spring projects, custom retry logic requirements.

---

## 8. Timezone Handling

**Decision:** Use Pacific Time (America/Los_Angeles)  
**Alternative Considered:** Local time (Berlin)

### The Problem

- Gemini quota resets: Midnight Pacific Time
- Development location: Berlin, Germany (PT + 8/9 hours)
- Mismatch causes quota window desynchronization

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

**Local Time:**
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

### Rationale

Pacific Time chosen for:
- Matches Gemini's authoritative quota reset schedule
- Works regardless of development/deployment location
- Automatic DST handling via Java ZoneId
- Prevents entire class of timezone-related bugs
- No manual reset logic needed

**Core principle:** In distributed systems, always use the authoritative service's timezone for quota/rate limit tracking.

---

## Summary

| Decision | Chosen Approach | Primary Rationale |
|----------|----------------|-------------------|
| Architecture | Batch processing | Simplicity, learning focus, proven pattern |
| Rate Limiting | Thread.sleep() | Solves actual constraint, zero dependencies |
| **Adaptive Rate Limiting** | **Response-based delay adjustment** | **Optimizes throughput, respectful behavior** |
| State Management | JSON + CSV | Fast resume, clean separation of concerns |
| Concurrency | Sequential | Only valid approach for rate-limited API |
| Checkpoint Frequency | Per-batch (10 tweets) | Aligns with rate limiting, negligible overhead |
| Checkpoint Cleanup | Auto-delete on success | Clean slate for next run, enables manual resume |
| Error Recovery | @Retryable | Industry standard, handles transient failures |
| Timezone | Pacific Time | Aligns with authoritative quota source |

---

## Key Learnings

1. **Architecture determines tooling** - Processing model (batch vs streaming) dictates rate limiting approach
2. **Rate limits are multi-dimensional** - RPM (easy) vs RPD (requires state management)
3. **Simplicity scales** - Thread.sleep() sufficient when concurrent execution provides no benefit
4. **State management drives design** - Separate checkpoint (resume) from output (results) for clarity
5. **Natural checkpoint points exist** - Rate limit pauses are ideal times to save state
6. **Crash safety is cheap** - JSON write (1ms) vs API call (2000ms) = negligible overhead
7. **User flexibility matters** - Manual cleanup strategy provides control over resume vs fresh start
8. **Set-based lookups scale** - O(1) contains() vs O(n) CSV scan for resume performance
9. **Adaptive rate limiting optimizes throughput** - Response-time-based adjustments (200ms-10s) respect API health while maximizing speed when possible
10. **Good citizen pattern** - Fast responses → speed up, slow/errors → back off creates respectful API client behavior

---

## References

- AWS: [Timeouts, Retries, and Backoff with Jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [HTTP Connection Management (MDN)](https://developer.mozilla.org/en-US/docs/Web/HTTP/Connection_management_in_HTTP_1.x)
- [Google Gemini API Documentation](https://ai.google.dev/gemini-api/docs/quota)
- [Jackson ObjectMapper Documentation](https://fasterxml.github.io/jackson-databind/javadoc/2.9/com/fasterxml/jackson/databind/ObjectMapper.html)
- [Spring Retry Reference](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- Personal reference: Python thesis project (LLM classification with Ollama)