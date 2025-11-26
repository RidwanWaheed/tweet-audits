package com.ridwan.tweetaudit.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker.QuotaExceededException;

class DailyQuotaTrackerTest {

  private static final Path QUOTA_FILE = Paths.get("results/daily_quota.json");

  private DailyQuotaTracker quotaTracker;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    quotaTracker = new DailyQuotaTracker(objectMapper);

    // Clean state for each test
    quotaTracker.reset();
  }

  @AfterEach
  void tearDown() throws IOException {
    // Clean up test files
    if (Files.exists(QUOTA_FILE)) {
      Files.delete(QUOTA_FILE);
    }
  }

  @Test
  void shouldStartWithFullQuota() {
    assertEquals(1000, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldDecrementQuotaAfterIncrement() throws QuotaExceededException {
    quotaTracker.checkQuota();
    quotaTracker.incrementRequestCount();

    assertEquals(999, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldPersistQuotaToFile() throws QuotaExceededException, IOException {
    quotaTracker.checkQuota();
    quotaTracker.incrementRequestCount();
    quotaTracker.incrementRequestCount();

    // Verify file was created
    assertTrue(Files.exists(QUOTA_FILE));

    // Load new tracker instance
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper);

    // Should load persisted state
    assertEquals(998, newTracker.getRemainingQuota());
  }

  @Test
  void shouldThrowExceptionWhenQuotaExceeded() {
    // Exhaust quota
    for (int i = 0; i < 1000; i++) {
      quotaTracker.incrementRequestCount();
    }

    // Should have no remaining quota
    assertEquals(0, quotaTracker.getRemainingQuota());

    // Should throw exception on next check
    QuotaExceededException exception =
        assertThrows(QuotaExceededException.class, () -> quotaTracker.checkQuota());

    assertTrue(exception.getMessage().contains("Daily quota exhausted"));
    assertTrue(exception.getMessage().contains("1000/1000 requests"));
  }

  @Test
  void shouldAllowRequestsUpToLimit() throws QuotaExceededException {
    // Use 999 requests (should be fine)
    for (int i = 0; i < 999; i++) {
      quotaTracker.checkQuota();
      quotaTracker.incrementRequestCount();
    }

    assertEquals(1, quotaTracker.getRemainingQuota());

    // Last request should succeed
    assertDoesNotThrow(() -> quotaTracker.checkQuota());
    quotaTracker.incrementRequestCount();

    assertEquals(0, quotaTracker.getRemainingQuota());

    // Next request should fail
    assertThrows(QuotaExceededException.class, () -> quotaTracker.checkQuota());
  }

  @Test
  void shouldHandleMultipleIncrements() {
    for (int i = 0; i < 10; i++) {
      quotaTracker.incrementRequestCount();
    }

    assertEquals(990, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldCreateFileIfNotExists() throws IOException {
    // Delete file if exists
    if (Files.exists(QUOTA_FILE)) {
      Files.delete(QUOTA_FILE);
    }

    // Create new tracker
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper);

    // Should start with full quota
    assertEquals(1000, newTracker.getRemainingQuota());

    // Should create file after first increment
    newTracker.incrementRequestCount();
    assertTrue(Files.exists(QUOTA_FILE));
  }

  @Test
  void shouldResetQuota() {
    // Use some quota
    for (int i = 0; i < 50; i++) {
      quotaTracker.incrementRequestCount();
    }

    assertEquals(950, quotaTracker.getRemainingQuota());

    // Reset quota
    quotaTracker.reset();

    // Should have full quota again
    assertEquals(1000, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldProvideQuotaResetTime() {
    String resetTime = quotaTracker.getQuotaResetTime();

    assertNotNull(resetTime);
    assertTrue(resetTime.contains("midnight PST"));
    assertTrue(resetTime.contains("in"));
    assertTrue(resetTime.contains("hours"));
  }

  @Test
  void shouldLogQuotaStatusWithoutError() {
    // Should not throw exception
    assertDoesNotThrow(() -> quotaTracker.logQuotaStatus());

    // Use some quota and log again
    quotaTracker.incrementRequestCount();
    assertDoesNotThrow(() -> quotaTracker.logQuotaStatus());
  }

  @Test
  void shouldHandleCorruptedQuotaFile() throws IOException {
    // Create corrupted file
    Files.createDirectories(QUOTA_FILE.getParent());
    Files.writeString(QUOTA_FILE, "invalid json content");

    // Should handle gracefully and create new state
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper);

    // Should start with full quota (fallback to new state)
    assertEquals(1000, newTracker.getRemainingQuota());
  }

  @Test
  void shouldReturnZeroWhenQuotaExhausted() {
    // Exhaust quota
    for (int i = 0; i < 1000; i++) {
      quotaTracker.incrementRequestCount();
    }

    // Should return exactly 0, not negative
    assertEquals(0, quotaTracker.getRemainingQuota());

    // Increment beyond limit
    quotaTracker.incrementRequestCount();

    // Should still return 0, not negative
    assertEquals(0, quotaTracker.getRemainingQuota());
  }
}
