package com.ridwan.tweetaudit.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ridwan.tweetaudit.model.TweetEvaluationResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CSVWriter {

  private final String outputPath;

  public CSVWriter(@Value("${output.csv-path}") String outputPath) {
    this.outputPath = outputPath;
  }

  public void writeResults(List<TweetEvaluationResult> results) throws IOException {
    log.info("Writing results to CSV: {}", outputPath);

    List<TweetEvaluationResult> flaggedTweets =
        results.stream().filter(TweetEvaluationResult::isShouldDelete).toList();

    log.info("Writing {} flagged tweets to CSV", flaggedTweets.size());

    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))) {
      writer.write("tweetUrl,tweetId,matchedCriteria,reason");
      writer.newLine();

      for (TweetEvaluationResult result : flaggedTweets) {
        writer.write(
            String.format(
                "%s,%s,%s,%s",
                tweetUrl(result.getTweetId()),
                result.getTweetId(),
                escapeCsv(String.join("|", result.getMatchedCriteria())),
                escapeCsv(result.getReason())));
        writer.newLine();
      }
    }

    log.info("CSV written successfully");
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
