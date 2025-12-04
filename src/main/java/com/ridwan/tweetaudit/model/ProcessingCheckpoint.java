package com.ridwan.tweetaudit.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingCheckpoint {

  @JsonProperty("last_processed_tweet_id")
  private String lastProcessedTweetId;

  @JsonProperty("processed_tweet_ids")
  @Builder.Default
  private Set<String> processedTweetIds = new HashSet<>();

  @JsonProperty("timestamp")
  private Instant timestamp;

  @JsonProperty("total_processed")
  private int totalProcessed;

  @JsonProperty("total_tweets")
  private int totalTweets;

  @JsonProperty("flagged_count")
  @Builder.Default
  private int flaggedCount = 0;

  @JsonProperty("error_count")
  @Builder.Default
  private int errorCount = 0;
}
