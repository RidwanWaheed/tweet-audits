package com.ridwan.tweetaudit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiCandidate {

    @JsonProperty("content")
    private GeminiContentResponse content;

    @JsonProperty("finishReason")
    private String finishReason;
    
    @JsonProperty("index")
    private Integer index;
}