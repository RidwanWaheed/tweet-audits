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

/**
 * Tracks daily API quota usage for Gemini API.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Persistent tracking across application restarts
 *   <li>Automatic reset at midnight Pacific Time (Gemini's quota reset schedule)
 *   <li>Pre-flight quota checks before processing
 *   <li>Configurable safety threshold to prevent quota overruns
 *   <li>Graceful stopping before hitting quota
 * </ul>
 *
 * <p>Gemini 2.5 Flash-Lite Free Tier Limits:
 *
 * <ul>
 *   <li>15 Requests Per Minute (RPM) - handled by AdaptiveRateLimiter
 *   <li>1,000 Requests Per Day (RPD) - handled by this tracker
 * </ul>
 *
 * <p>Safety Threshold:
 *
 * <ul>
 *   <li>Configurable via quota.safety-threshold property
 *   <li>Default: 950 (95% of daily limit)
 *   <li>Accounts for clock drift, race conditions, retry logic
 *   <li>50-request buffer provides margin for distributed system timing issues
 * </ul>
 */
@Component
@Slf4j
public class DailyQuotaTracker {

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

  /**
   * Load quota state from file or create new state if file doesn't exist.
   *
   * @return Current quota state
   */
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

  /**
   * Create new quota state for current day.
   *
   * @return New quota state
   */
  private QuotaState createNewQuotaState() {
    String currentDate = getCurrentDateInPacific();
    return new QuotaState(currentDate, 0);
  }

  /**
   * Save current quota state to file.
   */
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

  /**
   * Get current date in Pacific Time (yyyy-MM-dd format).
   *
   * @return Current date string
   */
  private String getCurrentDateInPacific() {
    return ZonedDateTime.now(PACIFIC_TIME).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  /**
   * Reset quota state if it's a new day in Pacific Time.
   */
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

  /**
   * Check if there's remaining quota before making a request.
   *
   * <p>Uses safety threshold instead of absolute daily limit to account for:
   * <ul>
   *   <li>Clock drift between client and Gemini servers
   *   <li>Race conditions in concurrent request processing
   *   <li>Retry logic that may count failed requests
   * </ul>
   *
   * @throws QuotaExceededException if safety threshold would be exceeded
   */
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

  /**
   * Increment request count after successful API call.
   */
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

  /**
   * Get remaining quota for today (based on safety threshold).
   *
   * @return Number of requests remaining before hitting safety threshold
   */
  public int getRemainingQuota() {
    resetIfNewDay();
    return Math.max(0, safetyThreshold - currentState.getRequestCount());
  }

  /**
   * Get quota reset time in human-readable format.
   *
   * @return Reset time string (e.g., "midnight PST (in 8 hours)")
   */
  public String getQuotaResetTime() {
    ZonedDateTime now = ZonedDateTime.now(PACIFIC_TIME);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(PACIFIC_TIME);

    long hoursUntilReset = java.time.Duration.between(now, midnight).toHours();

    return String.format("midnight PST (in %d hours)", hoursUntilReset);
  }

  /**
   * Log current quota status.
   */
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

  /**
   * Reset quota state (for testing).
   */
  void reset() {
    currentState = createNewQuotaState();
    saveQuotaState();
  }

  /**
   * Internal quota state representation.
   */
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
