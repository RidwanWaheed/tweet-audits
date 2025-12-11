package com.ridwan.tweetaudit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ridwan.tweetaudit.TestFixtures;
import com.ridwan.tweetaudit.checkpoint.CheckpointManager;
import com.ridwan.tweetaudit.client.GeminiClient;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;
import com.ridwan.tweetaudit.output.CSVWriter;
import com.ridwan.tweetaudit.parser.ArchiveParser;
import com.ridwan.tweetaudit.progress.ProgressTracker;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker;
import com.ridwan.tweetaudit.validation.ConfigValidator;

@ExtendWith(MockitoExtension.class)
class TweetAuditServiceTest {

  @Mock private ArchiveParser archiveParser;

  @Mock private GeminiClient geminiClient;

  @Mock private CSVWriter csvWriter;

  @Mock private AlignmentCriteria criteria;

  @Mock private CheckpointManager checkpointManager;

  @Mock private ConfigValidator configValidator;

  @Mock private ProgressTracker progressTracker;

  @Mock private DailyQuotaTracker quotaTracker;

  private TweetAuditService tweetAuditService;

  @BeforeEach
  void setUp() throws Exception {
    String archivePath = "test-archive.js";
    String apiKey = "test-api-key-that-is-long-enough";
    String outputPath = "test-output.csv";
    int batchSize = 10;

    when(checkpointManager.loadCheckpoint()).thenReturn(null);

    tweetAuditService =
        new TweetAuditService(
            archiveParser,
            geminiClient,
            csvWriter,
            criteria,
            checkpointManager,
            configValidator,
            progressTracker,
            quotaTracker,
            batchSize,
            archivePath,
            apiKey,
            outputPath);
  }

  @Test
  void shouldProcessTweetsSuccessfully() throws Exception {
    List<Tweet> mockTweets = List.of(TestFixtures.cleanTweet(), TestFixtures.flaggedTweet());

    TweetEvaluationResult cleanResult = TestFixtures.cleanTweetEvaluationResult();
    TweetEvaluationResult flaggedResult = TestFixtures.flaggedTweetEvaluationResult();

    when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
    when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
        .thenReturn(cleanResult)
        .thenReturn(flaggedResult);

    tweetAuditService.run();

    verify(archiveParser, times(1)).parseTweets(anyString());
    verify(geminiClient, times(2)).evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class));
    verify(csvWriter, times(1)).clearResults(); // Clear CSV at start
    verify(csvWriter, times(1)).appendResults(anyList()); // Append results after batch
    verify(checkpointManager, times(1)).loadCheckpoint();
    verify(checkpointManager, times(1)).deleteCheckpoint();
  }

  @Test
  void shouldHandleEvaluationErrors() throws Exception {
    List<Tweet> mockTweets =
        List.of(TestFixtures.tweet("1", "Tweet 1"), TestFixtures.tweet("2", "Tweet 2"));

    TweetEvaluationResult successResult = TestFixtures.cleanTweetEvaluationResult();

    when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
    when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
        .thenReturn(successResult)
        .thenThrow(new RuntimeException("API error"));

    tweetAuditService.run();

    verify(archiveParser, times(1)).parseTweets(anyString());
    verify(geminiClient, times(2)).evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class));
    verify(csvWriter, times(1)).clearResults(); // Clear CSV at start
    verify(csvWriter, times(1)).appendResults(anyList()); // Append results after batch
    verify(checkpointManager, times(1)).deleteCheckpoint();
  }

  @Test
  void shouldWriteResultsEvenWhenAllTweetsFail() throws Exception {
    List<Tweet> mockTweets = List.of(TestFixtures.tweet("1", "Tweet 1"));

    when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
    when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
        .thenThrow(new RuntimeException("API error"));

    tweetAuditService.run();

    verify(csvWriter, times(1)).clearResults(); // Clear CSV at start
    verify(csvWriter, times(1)).appendResults(anyList()); // Append results after batch
    verify(checkpointManager, times(1)).deleteCheckpoint();
  }
}
