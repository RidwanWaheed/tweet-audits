package com.ridwan.tweetaudit.ratelimit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker.QuotaExceededException;

class DailyQuotaTrackerTest {

  private static final Path QUOTA_FILE = Paths.get("results/daily_quota.json");
  private static final int DAILY_LIMIT = 1000;
  private static final int SAFETY_THRESHOLD = 950;

  private DailyQuotaTracker quotaTracker;
  private ObjectMapper objectMapper;
  private ApplicationContext mockAppContext;

  @BeforeEach
  void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    mockAppContext = mock(ApplicationContext.class);
    quotaTracker = new DailyQuotaTracker(objectMapper, mockAppContext, DAILY_LIMIT, SAFETY_THRESHOLD);

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
    // Remaining quota should be based on safety threshold
    assertEquals(SAFETY_THRESHOLD, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldDecrementQuotaAfterIncrement() throws QuotaExceededException {
    quotaTracker.checkQuota();
    quotaTracker.incrementRequestCount();

    assertEquals(SAFETY_THRESHOLD - 1, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldPersistQuotaToFile() throws QuotaExceededException, IOException {
    quotaTracker.checkQuota();
    quotaTracker.incrementRequestCount();
    quotaTracker.incrementRequestCount();

    // Verify file was created
    assertTrue(Files.exists(QUOTA_FILE));

    // Load new tracker instance
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper, mockAppContext, DAILY_LIMIT, SAFETY_THRESHOLD);

    // Should load persisted state
    assertEquals(SAFETY_THRESHOLD - 2, newTracker.getRemainingQuota());
  }

  @Test
  void shouldTriggerGracefulShutdownWhenSafetyThresholdReached() {
    // Create spy to intercept shutdown without actually calling System.exit()
    DailyQuotaTracker spyTracker = spy(quotaTracker);
    doNothing().when(spyTracker).performShutdown(anyInt());

    // Reach safety threshold
    for (int i = 0; i < SAFETY_THRESHOLD; i++) {
      quotaTracker.incrementRequestCount();
    }

    // Should have no remaining quota
    assertEquals(0, spyTracker.getRemainingQuota());

    // Should trigger graceful shutdown (not throw exception)
    assertDoesNotThrow(() -> spyTracker.checkQuota());

    // Verify SpringApplication.exit() would have been called
    verify(spyTracker, times(1)).performShutdown(0);
  }

  @Test
  void shouldAllowRequestsUpToSafetyThreshold() throws QuotaExceededException {
    // Create spy to intercept shutdown
    DailyQuotaTracker spyTracker = spy(quotaTracker);
    doNothing().when(spyTracker).performShutdown(anyInt());

    // Use up to safety threshold - 1 (should be fine)
    for (int i = 0; i < SAFETY_THRESHOLD - 1; i++) {
      spyTracker.checkQuota();
      spyTracker.incrementRequestCount();
    }

    assertEquals(1, spyTracker.getRemainingQuota());

    // Last request before threshold should succeed
    assertDoesNotThrow(() -> spyTracker.checkQuota());
    spyTracker.incrementRequestCount();
    assertEquals(0, spyTracker.getRemainingQuota());

    // Next request should trigger graceful shutdown
    assertDoesNotThrow(() -> spyTracker.checkQuota());

    // Verify shutdown was called
    verify(spyTracker, times(1)).performShutdown(0);
  }

  @Test
  void shouldHandleMultipleIncrements() {
    for (int i = 0; i < 10; i++) {
      quotaTracker.incrementRequestCount();
    }

    assertEquals(SAFETY_THRESHOLD - 10, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldCreateFileIfNotExists() throws IOException {
    // Delete file if exists
    if (Files.exists(QUOTA_FILE)) {
      Files.delete(QUOTA_FILE);
    }

    // Create new tracker
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper, mockAppContext, DAILY_LIMIT, SAFETY_THRESHOLD);

    // Should start with full quota (based on safety threshold)
    assertEquals(SAFETY_THRESHOLD, newTracker.getRemainingQuota());

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

    assertEquals(SAFETY_THRESHOLD - 50, quotaTracker.getRemainingQuota());

    // Reset quota
    quotaTracker.reset();

    // Should have full quota again (based on safety threshold)
    assertEquals(SAFETY_THRESHOLD, quotaTracker.getRemainingQuota());
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
    DailyQuotaTracker newTracker = new DailyQuotaTracker(objectMapper, mockAppContext, DAILY_LIMIT, SAFETY_THRESHOLD);

    // Should start with full quota (fallback to new state, based on safety threshold)
    assertEquals(SAFETY_THRESHOLD, newTracker.getRemainingQuota());
  }

  @Test
  void shouldReturnZeroWhenSafetyThresholdExhausted() {
    // Exhaust safety threshold
    for (int i = 0; i < SAFETY_THRESHOLD; i++) {
      quotaTracker.incrementRequestCount();
    }

    // Should return exactly 0, not negative
    assertEquals(0, quotaTracker.getRemainingQuota());

    // Increment beyond threshold
    quotaTracker.incrementRequestCount();

    // Should still return 0, not negative
    assertEquals(0, quotaTracker.getRemainingQuota());
  }

  @Test
  void shouldShutdownBeforeReachingActualDailyLimit() {
    // Create spy to intercept shutdown
    DailyQuotaTracker spyTracker = spy(quotaTracker);
    doNothing().when(spyTracker).performShutdown(anyInt());

    // Use requests up to safety threshold
    for (int i = 0; i < SAFETY_THRESHOLD; i++) {
      spyTracker.incrementRequestCount();
    }

    // Trigger shutdown at safety threshold (950)
    assertDoesNotThrow(() -> spyTracker.checkQuota());
    verify(spyTracker, times(1)).performShutdown(0);

    // This ensures we stopped BEFORE hitting the actual daily limit (1000)
    // Leaving a 50-request safety margin
  }

  @Test
  void shouldNotShutdownJustBeforeSafetyThreshold() throws QuotaExceededException {
    // Create spy to intercept shutdown
    DailyQuotaTracker spyTracker = spy(quotaTracker);
    doNothing().when(spyTracker).performShutdown(anyInt());

    // Use requests up to 1 before safety threshold
    for (int i = 0; i < SAFETY_THRESHOLD - 1; i++) {
      spyTracker.incrementRequestCount();
    }

    // Should not trigger shutdown yet
    assertDoesNotThrow(() -> spyTracker.checkQuota());
    verify(spyTracker, never()).performShutdown(anyInt());

    assertEquals(1, spyTracker.getRemainingQuota());
  }
}
