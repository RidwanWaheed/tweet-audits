package com.ridwan.tweetaudit.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.ridwan.tweetaudit.model.TweetEvaluationResult;

class CSVWriterTest {
    
    private CSVWriter csvWriter;
    private Path tempFile;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary file for testing
        tempFile = Files.createTempFile("test-tweets-", ".csv");
        csvWriter = new CSVWriter(tempFile.toString());
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Delete the temporary file after test
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
        }
    }
    
    @Test
    void shouldWriteFlaggedTweetsToCSV() throws IOException {
        List<TweetEvaluationResult> results = List.of(
            TweetEvaluationResult.builder()
                .tweetId("12345")
                .shouldDelete(true)
                .matchedCriteria(List.of("forbidden_words", "unprofessional"))
                .reason("Contains forbidden content")
                .errorMessage(null)
                .build()
        );

        csvWriter.writeResults(results);

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(2, lines.size(), "CSV should have header + 1 data row");
        assertEquals("tweetUrl,tweetId,status,matchedCriteria,reason", lines.get(0), "Header should match expected format");

        String dataRow = lines.get(1);
        assertTrue(dataRow.contains("https://x.com/i/status/12345"), "Should contain tweet URL");
        assertTrue(dataRow.contains("12345"), "Should contain tweet ID");
        assertTrue(dataRow.contains("FLAGGED"), "Should have FLAGGED status");
        assertTrue(dataRow.contains("forbidden_words|unprofessional"), "Should contain pipe-separated criteria");
        assertTrue(dataRow.contains("Contains forbidden content"), "Should contain reason");
    }

    @Test
    void shouldWriteErrorTweetsToCSV() throws IOException {
        List<TweetEvaluationResult> results = List.of(
            TweetEvaluationResult.builder()
                .tweetId("67890")
                .shouldDelete(false)
                .reason("")
                .errorMessage("API timeout after 3 retries")
                .build()
        );

        csvWriter.writeResults(results);

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(2, lines.size(), "CSV should have header + 1 error row");

        String dataRow = lines.get(1);
        assertTrue(dataRow.contains("67890"), "Should contain tweet ID");
        assertTrue(dataRow.contains("ERROR"), "Should have ERROR status");
        assertTrue(dataRow.contains("API timeout after 3 retries"), "Should contain error message");
    }

    @Test
    void shouldEscapeCommasAndQuotesInCSV() throws IOException {
        List<TweetEvaluationResult> results = List.of(
            TweetEvaluationResult.builder()
                .tweetId("11111")
                .shouldDelete(true)
                .matchedCriteria(List.of("test"))
                .reason("Contains \"quotes\" and, commas")
                .errorMessage(null)
                .build()
        );

        csvWriter.writeResults(results);

        List<String> lines = Files.readAllLines(tempFile);

        String dataRow = lines.get(1);
        assertTrue(dataRow.contains("\"Contains \"\"quotes\"\" and, commas\""),
            "Should properly escape quotes and wrap field with quotes");
    }

    @Test
    void shouldNotWriteCleanTweets() throws IOException {
        List<TweetEvaluationResult> results = List.of(
            TweetEvaluationResult.builder()
                .tweetId("99999")
                .shouldDelete(false)
                .reason("Tweet is clean")
                .errorMessage(null)
                .build()
        );

        csvWriter.writeResults(results);

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(1, lines.size(), "CSV should only have header (no clean tweets)");
        assertEquals("tweetUrl,tweetId,status,matchedCriteria,reason", lines.get(0));
    }

    @Test
    void shouldWriteMixedFlaggedAndErrorTweets() throws IOException {
        List<TweetEvaluationResult> results = List.of(
            TweetEvaluationResult.builder()
                .tweetId("11111")
                .shouldDelete(true)
                .matchedCriteria(List.of("forbidden_words"))
                .reason("Bad word detected")
                .errorMessage(null)
                .build(),
            TweetEvaluationResult.builder()
                .tweetId("22222")
                .shouldDelete(false)
                .reason("")
                .errorMessage("Network error")
                .build(),
            TweetEvaluationResult.builder()
                .tweetId("33333")
                .shouldDelete(false)
                .reason("Clean tweet")
                .errorMessage(null)
                .build()
        );

        csvWriter.writeResults(results);

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(3, lines.size(), "CSV should have header + 1 flagged + 1 error (no clean)");

        assertTrue(lines.stream().anyMatch(l -> l.contains("11111") && l.contains("FLAGGED")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("22222") && l.contains("ERROR")));
        assertFalse(lines.stream().anyMatch(l -> l.contains("33333")), "Should not include clean tweet");
    }
}