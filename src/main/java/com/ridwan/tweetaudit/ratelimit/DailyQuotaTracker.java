package com.ridwan.tweetaudit.ratelimit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DailyQuotaTracker {

  // Gemini resets daily quotas at midnight Pacific Time
  private static final ZoneId PACIFIC_TIME = ZoneId.of("America/Los_Angeles");

  private final ObjectMapper objectMapper;
  private final ApplicationContext appContext;
  private final Path quotaFilePath;
  private final int dailyLimit;
  private final int safetyThreshold;

  private QuotaState currentState;

  public DailyQuotaTracker(
      ObjectMapper objectMapper,
      ApplicationContext appContext,
      @Value("${quota.daily-limit}") int dailyLimit,
      @Value("${quota.safety-threshold}") int safetyThreshold,
      @Value("${quota.file-path:results/daily_quota.json}") String quotaFilePath) {
    this.objectMapper = objectMapper;
    this.appContext = appContext;
    this.quotaFilePath = Paths.get(quotaFilePath);
    this.dailyLimit = dailyLimit;
    this.safetyThreshold = safetyThreshold;
    this.currentState = loadOrCreateQuotaState();
  }

  private QuotaState loadOrCreateQuotaState() {
    try {
      if (Files.exists(quotaFilePath)) {
        QuotaState state = objectMapper.readValue(quotaFilePath.toFile(), QuotaState.class);
        log.info(
            "Loaded quota state: {} requests used on {}",
            state.getRequestCount(),
            state.getCurrentDate());
        return state;
      } else {
        log.info("No existing quota file found, creating new state");
        return createNewQuotaState();
      }
    } catch (IOException e) {
      log.warn("Failed to load quota state, creating new state: {}", e.getMessage());
      return createNewQuotaState();
    }
  }

  private QuotaState createNewQuotaState() {
    String currentDate = getCurrentDateInPacific();
    return new QuotaState(currentDate, 0);
  }

  private void saveQuotaState() {
    try {
      // Ensure results directory exists
      Files.createDirectories(quotaFilePath.getParent());

      // Write quota state to file
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(quotaFilePath.toFile(), currentState);

      log.debug("Saved quota state: {} requests on {}", currentState.getRequestCount(),
          currentState.getCurrentDate());

    } catch (IOException e) {
      log.error("Failed to save quota state: {}", e.getMessage());
    }
  }

  private String getCurrentDateInPacific() {
    return ZonedDateTime.now(PACIFIC_TIME).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private void resetIfNewDay() {
    String currentDate = getCurrentDateInPacific();

    if (!currentDate.equals(currentState.getCurrentDate())) {
      log.info(
          "New day detected! Resetting quota. Previous: {} requests on {}, New: {}",
          currentState.getRequestCount(),
          currentState.getCurrentDate(),
          currentDate);

      currentState = createNewQuotaState();
      saveQuotaState();
    }
  }

  // Check quota before making requests. Uses safety threshold (not daily limit) to account for
  // clock drift, race conditions, and retry logic discrepancies between client and server.
  public void checkQuota() throws QuotaExceededException {
    resetIfNewDay();

    if (currentState.getRequestCount() >= safetyThreshold) {
      String resetTime = getQuotaResetTime();

      log.error("═══════════════════════════════════════════════════");
      log.error("Safety threshold reached! ({}/{})", currentState.getRequestCount(), safetyThreshold);
      log.error("Daily limit: {}, Safety margin: {} requests", dailyLimit, dailyLimit - safetyThreshold);
      log.error("Quota resets at: {}", resetTime);
      log.error("Initiating graceful shutdown...");
      log.error("═══════════════════════════════════════════════════");

      // Trigger Spring cleanup (close connections, flush logs, etc.)
      int exitCode = SpringApplication.exit(appContext, () -> 0);

      // Allow time for Spring to complete cleanup tasks
      try {
          Thread.sleep(500);
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Shutdown interrupted during cleanup");
      }

      // Actually terminate the JVM
      performShutdown(exitCode);
    }

    // Log warning if approaching safety threshold (at 90% of threshold)
    int warningPoint = (int) (safetyThreshold * 0.9);
    if (currentState.getRequestCount() >= warningPoint && currentState.getRequestCount() < safetyThreshold) {
      log.warn(
          "⚠️  Approaching safety threshold! Used: {}/{} requests ({}% of threshold)",
          currentState.getRequestCount(),
          safetyThreshold,
          (currentState.getRequestCount() * 100) / safetyThreshold);
    }
  }

  public void incrementRequestCount() {
    resetIfNewDay();

    currentState.setRequestCount(currentState.getRequestCount() + 1);
    saveQuotaState();

    int remaining = getRemainingQuota();
    log.debug(
        "Quota updated: {}/{} requests used (threshold: {}), {} remaining",
        currentState.getRequestCount(),
        dailyLimit,
        safetyThreshold,
        remaining);
  }

  public int getRemainingQuota() {
    resetIfNewDay();
    return Math.max(0, safetyThreshold - currentState.getRequestCount());
  }

  public String getQuotaResetTime() {
    ZonedDateTime now = ZonedDateTime.now(PACIFIC_TIME);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(PACIFIC_TIME);

    long hoursUntilReset = java.time.Duration.between(now, midnight).toHours();

    return String.format("midnight PST (in %d hours)", hoursUntilReset);
  }

  public void logQuotaStatus() {
    resetIfNewDay();

    int remaining = getRemainingQuota();
    int percentUsed = (currentState.getRequestCount() * 100) / dailyLimit;
    int percentOfThreshold = (currentState.getRequestCount() * 100) / safetyThreshold;

    log.info(
        "=== Daily Quota Status ===\n"
            + "  Date: {}\n"
            + "  Used: {}/{} requests ({}% of daily limit)\n"
            + "  Safety threshold: {} ({}% used)\n"
            + "  Remaining: {} requests\n"
            + "  Resets: {}",
        currentState.getCurrentDate(),
        currentState.getRequestCount(),
        dailyLimit,
        percentUsed,
        safetyThreshold,
        percentOfThreshold,
        remaining,
        getQuotaResetTime());
  }

  void reset() {
    currentState = createNewQuotaState();
    saveQuotaState();
  }

  static class QuotaState {
    private String currentDate;
    private int requestCount;

    public QuotaState() {
      // Default constructor for Jackson
    }

    public QuotaState(String currentDate, int requestCount) {
      this.currentDate = currentDate;
      this.requestCount = requestCount;
    }

    public String getCurrentDate() {
      return currentDate;
    }

    public void setCurrentDate(String currentDate) {
      this.currentDate = currentDate;
    }

    public int getRequestCount() {
      return requestCount;
    }

    public void setRequestCount(int requestCount) {
      this.requestCount = requestCount;
    }
  }

  protected void performShutdown(int exitCode) {
    System.exit(exitCode);
  }

  public static class QuotaExceededException extends Exception {
    public QuotaExceededException(String message) {
      super(message);
    }
  }
}
