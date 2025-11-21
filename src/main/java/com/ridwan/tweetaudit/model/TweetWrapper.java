package com.ridwan.tweetaudit.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TweetWrapper {
    
    @JsonProperty("tweet")
    private Tweet tweet;

    public TweetWrapper() {
    }

    public Tweet getTweet() {
        return tweet;
    }

    public void setTweet(Tweet tweet) {
        this.tweet = tweet;
    }
}
