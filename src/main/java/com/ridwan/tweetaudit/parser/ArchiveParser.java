package com.ridwan.tweetaudit.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetWrapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ArchiveParser {

  private static final TypeReference<List<TweetWrapper>> TWEET_WRAPPER_LIST_TYPE =
      new TypeReference<List<TweetWrapper>>() {};

  private final ObjectMapper objectMapper;

  public ArchiveParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<Tweet> parseTweets(String filePath) throws IOException {
    log.info("Starting to parse tweets from: {}", filePath);

    String fileContent = readFile(filePath);

    String jsonContent = stripJavaScriptWrapper(fileContent);

    List<TweetWrapper> wrappers = parseJson(jsonContent);

    List<Tweet> tweets = extractAndFilter(wrappers);

    log.info("Successfully parsed {} tweets", tweets.size());

    return tweets;
  }

  private String readFile(String filePath) throws IOException {
    log.debug("Reading file: {}", filePath);
    return Files.readString(Path.of(filePath));
  }

  private String stripJavaScriptWrapper(String content) {
    log.debug("Stripping JavaScript wrapper");

    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("Archive file is empty");
    }

    int startIndex = content.indexOf('[');
    if (startIndex == -1) {
      throw new IllegalArgumentException("Invalid format: JSON array not found");
    }
    return content.substring(startIndex);
  }

  private List<TweetWrapper> parseJson(String json) throws IOException {
    log.debug("Parsing JSON to TweetWrapper list");
    return objectMapper.readValue(json, TWEET_WRAPPER_LIST_TYPE);
  }

  private List<Tweet> extractAndFilter(List<TweetWrapper> wrappers) {
    log.debug("Extracting and filtering {} tweet wrappers", wrappers.size());

    if (wrappers.isEmpty()) {
      log.warn("No tweets found in archive file");
      return List.of();
    }

    List<Tweet> filtered =
        wrappers.stream()
            .map(TweetWrapper::getTweet)
            .filter(tweet -> !tweet.isRetweet())
            .collect(Collectors.toList());

    log.info(
        "Filtered {} tweets, removed {} retweets",
        filtered.size(),
        wrappers.size() - filtered.size());

    return filtered;
  }
}
