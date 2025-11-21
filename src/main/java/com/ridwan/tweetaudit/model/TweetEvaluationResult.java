package com.ridwan.tweetaudit.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public class TweetEvaluationResult {

    private String tweetId;

    @JsonProperty("should_delete")
    private boolean shouldDelete;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("matched_criteria")
    private List<String> matchedCriteria;

    public TweetEvaluationResult() {
        this.matchedCriteria = new ArrayList<>();
    }

    public TweetEvaluationResult(String tweetId, boolean shouldDelete, String reason, List<String> matchedCriteria) {
        this.tweetId = tweetId;
        this.shouldDelete = shouldDelete;
        this.reason = reason;
        this.matchedCriteria = matchedCriteria != null ? matchedCriteria : new ArrayList<>();
    }

    public String getTweetId() {
        return tweetId;
    }

    public void setTweetId(String tweetId) {
        this.tweetId = tweetId;
    }

    public boolean isShouldDelete() {
        return shouldDelete;
    }

    public void setShouldDelete(boolean shouldDelete) {
        this.shouldDelete = shouldDelete;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getMatchedCriteria() {
        return matchedCriteria;
    }

    public void setMatchedCriteria(List<String> matchedCriteria) {
        this.matchedCriteria = matchedCriteria != null ? matchedCriteria : new ArrayList<>();
    }

    public boolean isValid() {
        return reason != null && matchedCriteria != null;
    }

    public String toString() {
        return "TweetEvaluationResult{" +
                "tweetId='" + tweetId + '\'' +
                ", shouldDelete=" + shouldDelete +
                ", reason='" + reason + '\'' +
                ", matchedCriteria=" + matchedCriteria +
                '}';
    }
    
}
