package com.ridwan.tweetaudit.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ridwan.tweetaudit.model.TweetEvaluationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CSVWriter {

  private final String outputPath;
  private boolean headerWritten = false;

  public CSVWriter(@Value("${output.csv-path}") String outputPath) {
    this.outputPath = outputPath;
  }

  public void writeResults(List<TweetEvaluationResult> results) throws IOException {
    log.info("Writing results to CSV: {}", outputPath);

    List<TweetEvaluationResult> flaggedOrErrorTweets =
        results.stream()
            .filter(r -> r.isShouldDelete() || r.getErrorMessage() != null)
            .toList();

    long flaggedCount = flaggedOrErrorTweets.stream().filter(r -> r.getErrorMessage() == null).count();
    long errorCount = flaggedOrErrorTweets.stream().filter(r -> r.getErrorMessage() != null).count();

    log.info("Writing {} flagged tweets and {} error tweets to CSV", flaggedCount, errorCount);

    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))) {
      writer.write("tweetUrl,tweetId,status,matchedCriteria,reason");
      writer.newLine();

      for (TweetEvaluationResult result : flaggedOrErrorTweets) {
        writeRow(writer, result);
      }
    }

    log.info("CSV written successfully");
  }

  public void appendResults(List<TweetEvaluationResult> results) throws IOException {
    List<TweetEvaluationResult> flaggedOrErrorTweets =
        results.stream()
            .filter(r -> r.isShouldDelete() || r.getErrorMessage() != null)
            .toList();

    if (flaggedOrErrorTweets.isEmpty()) {
      return;
    }

    Path path = Paths.get(outputPath);

    if (!headerWritten && !Files.exists(path)) {
      try (BufferedWriter writer = Files.newBufferedWriter(path)) {
        writer.write("tweetUrl,tweetId,status,matchedCriteria,reason");
        writer.newLine();
      }
      headerWritten = true;
    }

    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      for (TweetEvaluationResult result : flaggedOrErrorTweets) {
        writeRow(writer, result);
      }
    }

    log.debug("Appended {} results to CSV", flaggedOrErrorTweets.size());
  }

  private void writeRow(BufferedWriter writer, TweetEvaluationResult result) throws IOException {
    String status = result.getErrorMessage() != null ? "ERROR" : "FLAGGED";
    String reason = result.getErrorMessage() != null
        ? result.getErrorMessage()
        : result.getReason();
    String matchedCriteria = result.getErrorMessage() != null
        ? ""
        : String.join("|", result.getMatchedCriteria());

    writer.write(
        String.format(
            "%s,%s,%s,%s,%s",
            tweetUrl(result.getTweetId()),
            result.getTweetId(),
            status,
            escapeCsv(matchedCriteria),
            escapeCsv(reason)));
    writer.newLine();
  }

  private String tweetUrl(String tweetId) {
    if (tweetId == null) {
      return "";
    }
    return "https://x.com/i/status/" + tweetId;
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
