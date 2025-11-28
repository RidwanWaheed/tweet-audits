package com.ridwan.tweetaudit.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class TweetEvaluationResult {

  String tweetId;

  @JsonProperty("should_delete")
  boolean shouldDelete;

  @JsonProperty("reason")
  String reason;

  @Builder.Default
  @JsonProperty("matched_criteria")
  List<String> matchedCriteria = new ArrayList<>();

  String errorMessage;
}
