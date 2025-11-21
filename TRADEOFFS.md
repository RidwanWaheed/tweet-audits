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

## 3. State Management

**Decision:** Single CSV file  
**Alternative Considered:** Two separate CSVs (tweets + quota)

### Structure
```csv
tweet_id,processed,processed_date_pt,analysis_result,error
```

### Comparison

| Factor | Single CSV (Chosen) | Two CSVs |
|--------|---------------------|----------|
| Atomicity | Single write point | Two files must stay synchronized |
| Consistency | Cannot get out of sync | Risk of inconsistency on crash |
| Simplicity | One file to manage | Coordination logic needed |
| Quota Calculation | Derived (count rows by date) | Direct lookup |
| Crash Safety | One failure point | Two failure points |

### Rationale

Single CSV chosen for:
- Atomic updates - one write operation eliminates synchronization issues
- DRY principle - quota is derivable from processed tweets (count where date = today)
- Simpler code - no coordination between files needed
- Crash safety - cannot have inconsistent state between two files
- Proven pattern from Python thesis project
- Date already present in each tweet record

Two CSVs only beneficial when tracking complex quota metadata unrelated to individual tweets (not applicable here).

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

## 5. Checkpoint Strategy

**Decision:** Save after every tweet  
**Alternative Considered:** Batch checkpoints (every N tweets)

### Comparison

| Strategy | Max Loss on Crash | Quota Waste | Disk I/O | Code Complexity |
|----------|-------------------|-------------|----------|-----------------|
| Every Tweet (Chosen) | 1 tweet | ~0 requests | ~1ms per tweet | Simple |
| Every 10 Tweets | 10 tweets | Up to 10 requests | ~10ms per 10 tweets | More complex |

### Performance Analysis

- CSV write operation: ~0.1-1ms
- Gemini API call: ~2,000-3,000ms
- Overhead ratio: 0.025%

### Rationale

Per-tweet checkpointing chosen for:
- Negligible performance impact (API call dominates timing)
- Maximum crash safety - lose at most one tweet's progress
- Minimal quota waste - free tier has only 250 requests/day
- Simpler code - no batch buffer management
- Always reflects true processing state
- Proven effective in Python thesis project

Batch checkpointing appropriate for: Very high throughput systems (thousands/second), local operations only, when storage significantly slower than compute.

---

## 6. Error Recovery

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

Manual retry appropriate for: Non-Spring projects, custom retry logic requirements.

---

## 7. Timezone Handling

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
| State Management | Single CSV | Atomic updates, no synchronization issues |
| Concurrency | Sequential | Only valid approach for rate-limited API |
| Checkpointing | Per-tweet | Negligible overhead, maximum safety |
| Error Recovery | @Retryable | Industry standard, handles transient failures |
| Timezone | Pacific Time | Aligns with authoritative quota source |

---

## Key Learnings

1. **Architecture determines tooling** - Processing model (batch vs streaming) dictates rate limiting approach
2. **Rate limits are multi-dimensional** - RPM (easy) vs RPD (requires state management)
3. **Simplicity scales** - Thread.sleep() sufficient when concurrent execution provides no benefit
4. **State management is the complexity** - Focus effort on hard problems (timezone-aware quota tracking)
5. **Proven patterns transfer** - Python thesis project validated batch + single CSV + per-item checkpointing

---

## References

- AWS: [Timeouts, Retries, and Backoff with Jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [HTTP Connection Management (MDN)](https://developer.mozilla.org/en-US/docs/Web/HTTP/Connection_management_in_HTTP_1.x)
- [Google Gemini API Documentation](https://ai.google.dev/gemini-api/docs/quota)
- Personal reference: Python thesis project (LLM classification with Ollama)