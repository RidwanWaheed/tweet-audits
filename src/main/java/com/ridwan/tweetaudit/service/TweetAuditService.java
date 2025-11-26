package com.ridwan.tweetaudit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import com.ridwan.tweetaudit.checkpoint.CheckpointManager;
import com.ridwan.tweetaudit.client.GeminiClient;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.model.ProcessingCheckpoint;
import com.ridwan.tweetaudit.output.CSVWriter;
import com.ridwan.tweetaudit.parser.ArchiveParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TweetAuditService implements CommandLineRunner {

  private final ArchiveParser archiveParser;
  private final GeminiClient geminiClient;
  private final CSVWriter csvWriter;
  private final AlignmentCriteria criteria;
  private final CheckpointManager checkpointManager;
  private final int batchSize;
  private final String archivePath;

  public TweetAuditService(
      ArchiveParser archiveParser,
      GeminiClient geminiClient,
      CSVWriter csvWriter,
      AlignmentCriteria criteria,
      CheckpointManager checkpointManager,
      @Value("${tweet.processing.batch-size}") int batchSize,
      @Value("${archive.input-path}") String archivePath) {
    this.archiveParser = archiveParser;
    this.geminiClient = geminiClient;
    this.csvWriter = csvWriter;
    this.criteria = criteria;
    this.checkpointManager = checkpointManager;
    this.batchSize = batchSize;
    this.archivePath = archivePath;
  }

  @Override
  public void run(String... args) throws Exception {
    log.info("Starting tweet audit process...");
    log.info("Loading tweets from: {}", archivePath);

    List<Tweet> tweets = archiveParser.parseTweets(archivePath);
    log.info("Loaded {} tweets", tweets.size());

    ProcessingCheckpoint checkpoint = checkpointManager.loadCheckpoint();
    Set<String> processedTweetIds;
    List<TweetEvaluationResult> results = new ArrayList<>();
    int flaggedCount = 0;
    int errorCount = 0;

    if (checkpoint != null) {
      processedTweetIds = new HashSet<>(checkpoint.getProcessedTweetIds());
      flaggedCount = checkpoint.getFlaggedCount();
      errorCount = checkpoint.getErrorCount();
      log.info("Resuming from checkpoint: {} tweets already processed", processedTweetIds.size());
    } else {
      processedTweetIds = new HashSet<>();
      log.info("No checkpoint found, starting fresh");
    }

    final Set<String> processed = processedTweetIds;
    List<Tweet> remainingTweets =
        tweets.stream().filter(t -> !processed.contains(t.getIdStr())).toList();

    if (remainingTweets.isEmpty()) {
      log.info("All tweets already processed!");
      return;
    }

    log.info(
        "Processing {} remaining tweets (skipped {} already processed)",
        remainingTweets.size(),
        processedTweetIds.size());

    int totalBatches = (int) Math.ceil((double) remainingTweets.size() / batchSize);
    log.info(
        "Processing {} tweets in {} batches of {}",
        remainingTweets.size(),
        totalBatches,
        batchSize);

    for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
      int startIdx = batchNum * batchSize;
      int endIdx = Math.min(startIdx + batchSize, remainingTweets.size());
      List<Tweet> batch = remainingTweets.subList(startIdx, endIdx);

      log.info("Processing batch {}/{} ({} tweets)", batchNum + 1, totalBatches, batch.size());

      for (Tweet tweet : batch) {
        try {
          TweetEvaluationResult result = geminiClient.evaluateTweet(tweet, criteria);
          results.add(result);
          processedTweetIds.add(tweet.getIdStr());

          if (result.isShouldDelete()) {
            flaggedCount++;
          }
        } catch (Exception e) {
          log.error("Failed to evaluate tweet {}: {}", tweet.getIdStr(), e.getMessage());
          TweetEvaluationResult errorResult =
              TweetEvaluationResult.builder()
                  .tweetId(tweet.getIdStr())
                  .shouldDelete(false)
                  .reason("")
                  .errorMessage("Evaluation failed: " + e.getMessage())
                  .build();
          results.add(errorResult);
          processedTweetIds.add(tweet.getIdStr());
          errorCount++;
        }
      }

      ProcessingCheckpoint updatedCheckpoint =
          ProcessingCheckpoint.builder()
              .lastProcessedTweetId(batch.get(batch.size() - 1).getIdStr())
              .processedTweetIds(processedTweetIds)
              .totalProcessed(processedTweetIds.size())
              .totalTweets(tweets.size())
              .flaggedCount(flaggedCount)
              .errorCount(errorCount)
              .build();

      checkpointManager.saveCheckpoint(updatedCheckpoint);

      if (batchNum < totalBatches - 1) {
        log.info("Waiting 60 seconds before next batch (rate limiting)...");
        Thread.sleep(60000);
      }
    }

    log.info("Evaluation complete. Writing results to CSV...");
    csvWriter.writeResults(results);

    checkpointManager.deleteCheckpoint();
    log.info("Checkpoint deleted (processing complete)");

    long cleanCount = results.size() - flaggedCount - errorCount;

    log.info("=== Tweet Audit Complete ===");
    log.info("Total tweets processed: {}", results.size());
    log.info("Clean tweets: {}", cleanCount);
    log.info("Flagged for deletion: {}", flaggedCount);
    log.info("Errors: {}", errorCount);
  }
}
