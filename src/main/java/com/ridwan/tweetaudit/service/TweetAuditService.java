package com.ridwan.tweetaudit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import com.ridwan.tweetaudit.client.GeminiClient;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.output.CSVWriter;
import com.ridwan.tweetaudit.parser.ArchiveParser;

import java.util.ArrayList;
import java.util.List;

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
  private final int batchSize;
  private final String archivePath;

  public TweetAuditService(
      ArchiveParser archiveParser,
      GeminiClient geminiClient,
      CSVWriter csvWriter,
      AlignmentCriteria criteria,
      @Value("${tweet.processing.batch-size}") int batchSize,
      @Value("${archive.input-path}") String archivePath) {
    this.archiveParser = archiveParser;
    this.geminiClient = geminiClient;
    this.csvWriter = csvWriter;
    this.criteria = criteria;
    this.batchSize = batchSize;
    this.archivePath = archivePath;
  }

  @Override
  public void run(String... args) throws Exception {
    log.info("Starting tweet audit process...");
    log.info("Loading tweets from: {}", archivePath);

    List<Tweet> tweets = archiveParser.parseTweets(archivePath);
    log.info("Loaded {} tweets", tweets.size());

    List<TweetEvaluationResult> results = new ArrayList<>();

    int totalBatches = (int) Math.ceil((double) tweets.size() / batchSize);
    log.info("Processing {} tweets in {} batches of {}", tweets.size(), totalBatches, batchSize);

    for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
      int startIdx = batchNum * batchSize;
      int endIdx = Math.min(startIdx + batchSize, tweets.size());
      List<Tweet> batch = tweets.subList(startIdx, endIdx);

      log.info("Processing batch {}/{} ({} tweets)", batchNum + 1, totalBatches, batch.size());

      for (Tweet tweet : batch) {
        try {
          TweetEvaluationResult result = geminiClient.evaluateTweet(tweet, criteria);
          results.add(result);
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
        }
      }

      if (batchNum < totalBatches - 1) {
        log.info("Waiting 60 seconds before next batch (rate limiting)...");
        Thread.sleep(60000);
      }
    }

    log.info("Evaluation complete. Writing results to CSV...");
    csvWriter.writeResults(results);

    long flaggedCount = results.stream().filter(r -> r.isShouldDelete()).count();
    long errorCount = results.stream().filter(r -> r.getErrorMessage() != null).count();
    long cleanCount = results.size() - flaggedCount - errorCount;

    log.info("=== Tweet Audit Complete ===");
    log.info("Total tweets processed: {}", results.size());
    log.info("Clean tweets: {}", cleanCount);
    log.info("Flagged for deletion: {}", flaggedCount);
    log.info("Errors: {}", errorCount);
  }
}
