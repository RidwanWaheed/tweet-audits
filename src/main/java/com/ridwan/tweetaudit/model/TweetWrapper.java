package com.ridwan.tweetaudit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class TweetWrapper {
    
    @JsonProperty("tweet")
    Tweet tweet;

}
