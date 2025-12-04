package com.ridwan.tweetaudit.progress;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks and logs progress for tweet processing with ETA calculations.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Real-time progress updates
 *   <li>Processing rate calculation (tweets/minute)
 *   <li>ETA estimation
 *   <li>Smart logging (every N tweets or time interval)
 * </ul>
 */
@Component
@Slf4j
public class ProgressTracker {

  private static final int LOG_INTERVAL_TWEETS = 10;
  private static final long LOG_INTERVAL_SECONDS = 30;

  private int totalTweets;
  private int processedTweets;
  private Instant startTime;
  private Instant lastLogTime;

  /**
   * Initialize progress tracking for a new batch.
   *
   * @param total Total number of tweets to process
   */
  public void start(int total) {
    this.totalTweets = total;
    this.processedTweets = 0;
    this.startTime = Instant.now();
    this.lastLogTime = Instant.now();

    log.info("=== Progress Tracking Started ===");
    log.info("Total tweets to process: {}", total);
  }

  /**
   * Update progress after processing a tweet.
   */
  public void increment() {
    processedTweets++;

    // Log if we hit tweet interval or time interval
    if (shouldLog()) {
      logProgress();
      lastLogTime = Instant.now();
    }
  }

  /**
   * Complete progress tracking and log final summary.
   */
  public void complete() {
    logProgress();

    Duration totalDuration = Duration.between(startTime, Instant.now());
    log.info("=== Processing Complete ===");
    log.info("Total time: {}", formatDuration(totalDuration));
  }

  private boolean shouldLog() {
    // Log every N tweets
    if (processedTweets % LOG_INTERVAL_TWEETS == 0) {
      return true;
    }

    // Log every X seconds
    Duration timeSinceLastLog = Duration.between(lastLogTime, Instant.now());
    if (timeSinceLastLog.getSeconds() >= LOG_INTERVAL_SECONDS) {
      return true;
    }

    return false;
  }

  private void logProgress() {
    double percentComplete = (processedTweets * 100.0) / totalTweets;
    Duration elapsed = Duration.between(startTime, Instant.now());

    // Calculate rate (tweets per minute)
    double elapsedMinutes = elapsed.getSeconds() / 60.0;
    double rate = elapsedMinutes > 0 ? processedTweets / elapsedMinutes : 0;

    // Calculate ETA
    int remaining = totalTweets - processedTweets;
    String eta = calculateETA(remaining, rate);

    log.info(
        "Progress: {}/{} tweets ({:.1f}%) | Rate: {:.1f} tweets/min | ETA: {}",
        processedTweets,
        totalTweets,
        percentComplete,
        rate,
        eta);
  }

  private String calculateETA(int remaining, double rate) {
    if (rate <= 0 || remaining <= 0) {
      return "calculating...";
    }

    double minutesRemaining = remaining / rate;
    Duration etaDuration = Duration.ofSeconds((long) (minutesRemaining * 60));

    return formatDuration(etaDuration);
  }

  private String formatDuration(Duration duration) {
    long hours = duration.toHours();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();

    if (hours > 0) {
      return String.format("%dh %dm %ds", hours, minutes, seconds);
    } else if (minutes > 0) {
      return String.format("%dm %ds", minutes, seconds);
    } else {
      return String.format("%ds", seconds);
    }
  }

  /**
   * Get current progress percentage.
   *
   * @return Percentage complete (0-100)
   */
  public double getPercentComplete() {
    if (totalTweets == 0) {
      return 0;
    }
    return (processedTweets * 100.0) / totalTweets;
  }

  /**
   * Get number of processed tweets.
   *
   * @return Processed count
   */
  public int getProcessedCount() {
    return processedTweets;
  }
}
