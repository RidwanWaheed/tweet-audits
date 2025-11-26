package com.ridwan.tweetaudit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ridwan.tweetaudit.checkpoint.CheckpointManager;
import com.ridwan.tweetaudit.client.GeminiClient;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;
import com.ridwan.tweetaudit.output.CSVWriter;
import com.ridwan.tweetaudit.parser.ArchiveParser;
import com.ridwan.tweetaudit.validation.ConfigValidator;

@ExtendWith(MockitoExtension.class)
class TweetAuditServiceTest {

    @Mock
    private ArchiveParser archiveParser;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private CSVWriter csvWriter;

    @Mock
    private AlignmentCriteria criteria;

    @Mock
    private CheckpointManager checkpointManager;

    @Mock
    private ConfigValidator configValidator;

    private TweetAuditService tweetAuditService;

    @BeforeEach
    void setUp() throws Exception {
        String archivePath = "test-archive.js";
        String apiKey = "test-api-key-that-is-long-enough";
        String outputPath = "test-output.csv";
        int batchSize = 10;

        when(checkpointManager.loadCheckpoint()).thenReturn(null);

        tweetAuditService = new TweetAuditService(
            archiveParser,
            geminiClient,
            csvWriter,
            criteria,
            checkpointManager,
            configValidator,
            batchSize,
            archivePath,
            apiKey,
            outputPath
        );
    }

    @Test
    void shouldProcessTweetsSuccessfully() throws Exception {
        List<Tweet> mockTweets = List.of(
            Tweet.builder().idStr("1").fullText("Clean tweet").createdAt("2024-01-01").build(),
            Tweet.builder().idStr("2").fullText("Bad tweet with kill").createdAt("2024-01-02").build()
        );

        TweetEvaluationResult cleanResult = TweetEvaluationResult.builder()
            .tweetId("1")
            .shouldDelete(false)
            .reason("Clean")
            .build();

        TweetEvaluationResult flaggedResult = TweetEvaluationResult.builder()
            .tweetId("2")
            .shouldDelete(true)
            .reason("Contains forbidden word")
            .matchedCriteria(List.of("forbidden_words"))
            .build();

        when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
        when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
            .thenReturn(cleanResult)
            .thenReturn(flaggedResult);

        tweetAuditService.run();

        verify(archiveParser, times(1)).parseTweets(anyString());
        verify(geminiClient, times(2)).evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class));
        verify(csvWriter, times(1)).writeResults(anyList());
        verify(checkpointManager, times(1)).loadCheckpoint();
        verify(checkpointManager, times(1)).deleteCheckpoint();
    }

    @Test
    void shouldHandleEvaluationErrors() throws Exception {
        List<Tweet> mockTweets = List.of(
            Tweet.builder().idStr("1").fullText("Tweet 1").createdAt("2024-01-01").build(),
            Tweet.builder().idStr("2").fullText("Tweet 2").createdAt("2024-01-02").build()
        );

        TweetEvaluationResult successResult = TweetEvaluationResult.builder()
            .tweetId("1")
            .shouldDelete(false)
            .reason("Clean")
            .build();

        when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
        when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
            .thenReturn(successResult)
            .thenThrow(new RuntimeException("API error"));

        tweetAuditService.run();

        verify(archiveParser, times(1)).parseTweets(anyString());
        verify(geminiClient, times(2)).evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class));
        verify(csvWriter, times(1)).writeResults(anyList());
        verify(checkpointManager, times(1)).deleteCheckpoint();
    }

    @Test
    void shouldWriteResultsEvenWhenAllTweetsFail() throws Exception {
        List<Tweet> mockTweets = List.of(
            Tweet.builder().idStr("1").fullText("Tweet 1").createdAt("2024-01-01").build()
        );

        when(archiveParser.parseTweets(anyString())).thenReturn(mockTweets);
        when(geminiClient.evaluateTweet(any(Tweet.class), any(AlignmentCriteria.class)))
            .thenThrow(new RuntimeException("API error"));

        tweetAuditService.run();

        verify(csvWriter, times(1)).writeResults(anyList());
        verify(checkpointManager, times(1)).deleteCheckpoint();
    }
}
