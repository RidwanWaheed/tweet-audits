package com.ridwan.tweetaudit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tweet {

  @JsonProperty("id_str")
  String idStr;

  @JsonProperty("full_text")
  String fullText;

  @JsonProperty("created_at")
  String createdAt;

  public boolean isRetweet() {
    return fullText != null && fullText.startsWith("RT @");
  }

  public String getTweetUrl() {
    return "https://x.com/i/status/" + idStr;
  }
}
