package com.ridwan.tweetaudit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Tweet {

    @JsonProperty("id_str")
    private String idStr;

    @JsonProperty("full_text")
    private String fullText;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("favorite_count")
    private String favoriteCount;

    @JsonProperty("retweet_count")
    private String retweetCount;


    public Tweet() {
    }

    public String getIdStr() {
        return idStr;
    }

    public void setIdStr(String idStr) {
        this.idStr = idStr;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(String favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public String getRetweetCount() {
        return retweetCount;
    }

    public void setRetweetCount(String retweetCount) {
        this.retweetCount = retweetCount;
    }

    public boolean isRetweet() {
        return fullText != null && fullText.startsWith("RT @");
    }

    @Override
    public String toString() {
        return "Tweet [idStr=" + idStr + ", fullText=" + fullText + ", createdAt=" + createdAt
                + ", favoriteCount=" + favoriteCount + ", retweetCount=" + retweetCount + "]";
    }

}