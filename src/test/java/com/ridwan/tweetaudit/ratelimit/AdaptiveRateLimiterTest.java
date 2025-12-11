package com.ridwan.tweetaudit.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveRateLimiterTest {

  private AdaptiveRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter = new AdaptiveRateLimiter();
  }

  @Test
  void shouldStartWithInitialDelay() {
    assertEquals(1000, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldSpeedUpOnFastResponse() {
    // Fast response (< 500ms) should reduce delay
    rateLimiter.recordSuccess(300);

    assertEquals(900, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldSlowDownOnSlowResponse() {
    // Slow response (> 3000ms) should increase delay
    rateLimiter.recordSuccess(4000);

    assertEquals(1500, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldMaintainDelayOnNormalResponse() {
    // Normal response (500-3000ms) should keep delay unchanged
    rateLimiter.recordSuccess(1500);

    assertEquals(1000, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldNotGoBelow200msMinimum() {
    // Even with many fast responses, delay should not go below 200ms
    for (int i = 0; i < 20; i++) {
      rateLimiter.recordSuccess(100);
    }

    assertEquals(200, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldNotGoAbove10sMaximum() {
    // Even with many slow responses, delay should not exceed 10s
    for (int i = 0; i < 50; i++) {
      rateLimiter.recordSuccess(5000);
    }

    assertEquals(10_000, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldBackOffAggressivelyOnRateLimit() {
    // Rate limit hit should double the delay
    long initialDelay = rateLimiter.getCurrentDelayMs();
    rateLimiter.recordRateLimitHit();

    assertEquals(initialDelay * 2, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldBackOffModeratelyOnServerError() {
    // Server error should increase delay by 500ms
    long initialDelay = rateLimiter.getCurrentDelayMs();
    rateLimiter.recordServerError();

    assertEquals(initialDelay + 500, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldResetToInitialDelay() {
    // Change delay first
    rateLimiter.recordSuccess(100);
    assertEquals(900, rateLimiter.getCurrentDelayMs());

    // Reset should bring it back to 1000ms
    rateLimiter.reset();
    assertEquals(1000, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldCapRateLimitBackoffAtMaximum() {
    // Set delay close to maximum
    for (int i = 0; i < 30; i++) {
      rateLimiter.recordSuccess(5000);
    }

    // Rate limit hit should double, but cap at 10s max
    rateLimiter.recordRateLimitHit();
    assertEquals(10_000, rateLimiter.getCurrentDelayMs());
  }

  @Test
  void shouldWaitCorrectAmount() throws InterruptedException {
    long startTime = System.currentTimeMillis();

    rateLimiter.waitBeforeNextCall();

    long elapsedTime = System.currentTimeMillis() - startTime;

    // Should wait approximately 1000ms (initial delay)
    // Allow 100ms tolerance for execution overhead
    assertTrue(elapsedTime >= 900 && elapsedTime <= 1100,
        "Expected ~1000ms wait, got " + elapsedTime + "ms");
  }

  @Test
  void shouldAdaptThroughMultipleScenarios() {
    // Simulate real-world scenario: fast responses speed up
    rateLimiter.recordSuccess(300);
    rateLimiter.recordSuccess(400);
    assertEquals(800, rateLimiter.getCurrentDelayMs());

    // Then hit rate limit - back off aggressively
    rateLimiter.recordRateLimitHit();
    assertEquals(1600, rateLimiter.getCurrentDelayMs());

    // Slow responses increase delay further
    rateLimiter.recordSuccess(4000);
    assertEquals(2100, rateLimiter.getCurrentDelayMs());

    // Fast responses gradually recover
    rateLimiter.recordSuccess(300);
    assertEquals(2000, rateLimiter.getCurrentDelayMs());
  }
}
