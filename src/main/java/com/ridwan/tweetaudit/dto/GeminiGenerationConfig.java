package com.ridwan.tweetaudit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiGenerationConfig {

    @JsonProperty("responseMimeType")
    private String responseMimeType;

    // Using Map<String, Object> allows flexibility for any schema structure
    @JsonProperty("responseJsonSchema") 
    private Map<String, Object> responseJsonSchema;
    
    @JsonProperty("temperature")
    private Double temperature;
}