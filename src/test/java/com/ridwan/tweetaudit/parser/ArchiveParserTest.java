package com.ridwan.tweetaudit.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ridwan.tweetaudit.model.Tweet;

@SpringBootTest
class ArchiveParserTest {

  @Autowired private ArchiveParser archiveParser;

  @Test
  void shouldParseValidArchiveFile() throws IOException {
    // Given: A valid tweets.js file in test resources
    String filePath = "src/test/resources/sample-tweets.js";

    // When: Parsing the file
    List<Tweet> tweets = archiveParser.parseTweets(filePath);

    // Then: Should return non-empty list of tweets
    assertNotNull(tweets, "Tweet list should not be null");
    assertFalse(tweets.isEmpty(), "Tweet list should not be empty");

    // Should filter out retweets (1 retweet in sample file)
    assertEquals(3, tweets.size(), "Should have 3 original tweets (1 retweet filtered)");
  }

  @Test
  void shouldExtractCorrectTweetData() throws IOException {
    // Given: A valid tweets.js file
    String filePath = "src/test/resources/sample-tweets.js";

    // When: Parsing the file
    List<Tweet> tweets = archiveParser.parseTweets(filePath);

    // Then: Should extract correct data
    Tweet firstTweet = tweets.get(0);
    assertEquals("1234567890", firstTweet.getIdStr());
    assertEquals("This is a normal tweet about coding", firstTweet.getFullText());
    assertNotNull(firstTweet.getCreatedAt());
  }

  @Test
  void shouldFilterOutRetweets() throws IOException {
    // Given: A valid tweets.js file with retweets
    String filePath = "src/test/resources/sample-tweets.js";

    // When: Parsing the file
    List<Tweet> tweets = archiveParser.parseTweets(filePath);

    // Then: Should not contain any retweets
    boolean hasRetweets = tweets.stream().anyMatch(Tweet::isRetweet);
    assertFalse(hasRetweets, "Should filter out all retweets");
  }

  @Test
  void shouldGenerateValidTweetUrls() throws IOException {
    // Given: A valid tweets.js file
    String filePath = "src/test/resources/sample-tweets.js";

    // When: Parsing the file
    List<Tweet> tweets = archiveParser.parseTweets(filePath);

    // Then: Should be able to generate URLs
    Tweet firstTweet = tweets.get(0);
    String url = firstTweet.getTweetUrl();
    assertNotNull(url);
    assertTrue(url.startsWith("https://x.com/i/status/"));
    assertTrue(url.contains(firstTweet.getIdStr()));
  }

  @Test
  void shouldThrowExceptionForInvalidFormat(@TempDir Path tempDir) throws IOException {
    // Given: A file with invalid format (no JSON array)
    Path invalidFile = tempDir.resolve("invalid.js");
    Files.writeString(invalidFile, "window.YTD.tweets.part0 = {}");

    // When/Then: Should throw exception
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          archiveParser.parseTweets(invalidFile.toString());
        });
  }

  @Test
  void shouldThrowExceptionForNonExistentFile() {
    // Given: A non-existent file path
    String nonExistentPath = "/path/that/does/not/exist/tweets.js";

    // When/Then: Should throw IOException
    assertThrows(
        IOException.class,
        () -> {
          archiveParser.parseTweets(nonExistentPath);
        });
  }

  @Test
  void shouldHandleEmptyArchive(@TempDir Path tempDir) throws IOException {
    // Given: An empty array in archive
    Path emptyArchive = tempDir.resolve("empty-tweets.js");
    Files.writeString(emptyArchive, "window.YTD.tweets.part0 = []");

    // When: Parsing empty archive
    List<Tweet> tweets = archiveParser.parseTweets(emptyArchive.toString());

    // Then: Should return empty list (not null)
    assertNotNull(tweets);
    assertTrue(tweets.isEmpty(), "Should return empty list for empty archive");
  }
}
