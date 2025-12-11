package com.ridwan.tweetaudit;

import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;
import java.util.List;

public class TestFixtures {

  public static Tweet tweet(String id, String text) {
    return Tweet.builder().idStr(id).fullText(text).createdAt("2024-01-01").build();
  }

  public static Tweet cleanTweet() {
    return tweet("1", "Just finished a great coding session!");
  }

  public static Tweet flaggedTweet() {
    return tweet("2", "This is killing me!");
  }

  public static TweetEvaluationResult tweetEvaluationResult(
      String id,
      boolean shouldDelete,
      String reason,
      List<String> matchedCriteria,
      String errorMessage) {
    return TweetEvaluationResult.builder()
        .tweetId(id)
        .shouldDelete(shouldDelete)
        .matchedCriteria(matchedCriteria)
        .reason(reason)
        .errorMessage(errorMessage)
        .build();
  }

  public static TweetEvaluationResult tweetEvaluationResult(
      String id, boolean shouldDelete, String reason) {
    return tweetEvaluationResult(id, shouldDelete, reason, List.of(), null);
  }

  public static TweetEvaluationResult cleanTweetEvaluationResult() {
    return tweetEvaluationResult("1", false, "Clean tweet");
  }

  public static TweetEvaluationResult flaggedTweetEvaluationResult() {
    return tweetEvaluationResult("2", true, "Bad word detected", List.of("forbidden_words"), null);
  }
}
