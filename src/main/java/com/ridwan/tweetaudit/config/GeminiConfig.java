package com.ridwan.tweetaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiConfig {
  private String key;
  private String url;
  private RateLimit rateLimit;

  @Data
  public static class RateLimit {
    private int requestsPerMinute;
  }
}
