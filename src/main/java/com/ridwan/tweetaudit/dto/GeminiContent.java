package com.ridwan.tweetaudit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiContent {
    
    @JsonProperty("role")
    @Builder.Default
    private String role = "user";

    @JsonProperty("parts")
    private List<GeminiPart> parts;
}