package com.ridwan.tweetaudit.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adaptive rate limiter that adjusts delays based on API response times and status codes.
 *
 * <p>Strategy:
 *
 * <ul>
 *   <li>Fast responses (< 500ms): Gradually speed up (reduce delay)
 *   <li>Slow responses (> 3s): Gradually slow down (increase delay)
 *   <li>Rate limit hit (429): Back off aggressively (double delay)
 *   <li>Server errors (5xx): Back off moderately
 * </ul>
 *
 * <p>Delay bounds: 200ms (minimum) to 10s (maximum)
 */
@Component
@Slf4j
public class AdaptiveRateLimiter {

  // Delay bounds
  private static final long MIN_DELAY_MS = 200;
  private static final long MAX_DELAY_MS = 10_000;

  // Initial delay (conservative start)
  private static final long INITIAL_DELAY_MS = 1000;

  // Response time thresholds
  private static final long FAST_RESPONSE_THRESHOLD_MS = 500;
  private static final long SLOW_RESPONSE_THRESHOLD_MS = 3000;

  // Delay adjustments
  private static final long SPEED_UP_AMOUNT_MS = 100;
  private static final long SLOW_DOWN_AMOUNT_MS = 500;

  // Current delay (starts at initial, adapts over time)
  private long currentDelayMs = INITIAL_DELAY_MS;

  /**
   * Wait before making the next API call. The delay adapts based on previous call performance.
   *
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public void waitBeforeNextCall() throws InterruptedException {
    if (currentDelayMs > 0) {
      log.debug("Waiting {}ms before next API call", currentDelayMs);
      Thread.sleep(currentDelayMs);
    }
  }

  /**
   * Record a successful API call and adjust delay based on response time.
   *
   * @param responseTimeMs the time it took to get a response (milliseconds)
   */
  public void recordSuccess(long responseTimeMs) {
    long previousDelay = currentDelayMs;

    if (responseTimeMs < FAST_RESPONSE_THRESHOLD_MS) {
      // API is fast and responsive - speed up
      currentDelayMs = Math.max(MIN_DELAY_MS, currentDelayMs - SPEED_UP_AMOUNT_MS);
      log.debug(
          "Fast response ({}ms) - reducing delay from {}ms to {}ms",
          responseTimeMs,
          previousDelay,
          currentDelayMs);

    } else if (responseTimeMs > SLOW_RESPONSE_THRESHOLD_MS) {
      // API is slow - be more respectful
      currentDelayMs = Math.min(MAX_DELAY_MS, currentDelayMs + SLOW_DOWN_AMOUNT_MS);
      log.debug(
          "Slow response ({}ms) - increasing delay from {}ms to {}ms",
          responseTimeMs,
          previousDelay,
          currentDelayMs);

    } else {
      // Normal response time - keep current delay
      log.debug("Normal response ({}ms) - maintaining delay at {}ms", responseTimeMs, currentDelayMs);
    }
  }

  /**
   * Record a rate limit error (429 status code) and back off aggressively.
   */
  public void recordRateLimitHit() {
    long previousDelay = currentDelayMs;

    // Double the delay (exponential backoff)
    currentDelayMs = Math.min(MAX_DELAY_MS, currentDelayMs * 2);

    log.warn(
        "Rate limit hit (429) - backing off aggressively from {}ms to {}ms",
        previousDelay,
        currentDelayMs);
  }

  /**
   * Record a server error (5xx status code) and back off moderately.
   */
  public void recordServerError() {
    long previousDelay = currentDelayMs;

    // Increase delay moderately
    currentDelayMs = Math.min(MAX_DELAY_MS, currentDelayMs + SLOW_DOWN_AMOUNT_MS);

    log.warn(
        "Server error (5xx) - backing off moderately from {}ms to {}ms",
        previousDelay,
        currentDelayMs);
  }

  /**
   * Reset the rate limiter to initial state. Useful for starting a new batch.
   */
  public void reset() {
    log.debug("Resetting rate limiter from {}ms to {}ms", currentDelayMs, INITIAL_DELAY_MS);
    currentDelayMs = INITIAL_DELAY_MS;
  }

  /**
   * Get the current delay in milliseconds.
   *
   * @return current delay between API calls
   */
  public long getCurrentDelayMs() {
    return currentDelayMs;
  }
}
