package com.ridwan.tweetaudit.client;

import java.util.List;
import java.util.Map;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.config.GeminiConfig;
import com.ridwan.tweetaudit.dto.GeminiCandidate;
import com.ridwan.tweetaudit.dto.GeminiContent;
import com.ridwan.tweetaudit.dto.GeminiContentResponse;
import com.ridwan.tweetaudit.dto.GeminiGenerationConfig;
import com.ridwan.tweetaudit.dto.GeminiPart;
import com.ridwan.tweetaudit.dto.GeminiRequest;
import com.ridwan.tweetaudit.dto.GeminiResponse;
import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;
import com.ridwan.tweetaudit.ratelimit.AdaptiveRateLimiter;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker.QuotaExceededException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiClient {

  private final ObjectMapper objectMapper;
  private final GeminiConfig geminiConfig;
  private final WebClient webClient;
  private final AdaptiveRateLimiter rateLimiter;
  private final DailyQuotaTracker quotaTracker;

  public GeminiClient(
      ObjectMapper objectMapper,
      GeminiConfig geminiConfig,
      WebClient webClient,
      AdaptiveRateLimiter rateLimiter,
      DailyQuotaTracker quotaTracker) {
    this.objectMapper = objectMapper;
    this.geminiConfig = geminiConfig;
    this.webClient = webClient;
    this.rateLimiter = rateLimiter;
    this.quotaTracker = quotaTracker;
  }

  private String buildPrompt(Tweet tweet, AlignmentCriteria criteria) {
    String prompt =
        """
        You are evaluating tweets for a %s.

        Analyze this tweet and determine if it should be deleted based on these criteria:
        - Forbidden words: %s
        - Must be professional: %s
        - Desired tone: %s

        Tweet: "%s"

        Should this tweet be deleted? If yes, explain why and which criteria it violates.
        """
            .formatted(
                criteria.getContext(),
                String.join(", ", criteria.getForbiddenWords()),
                criteria.isCheckProfessionalism() ? "yes" : "no",
                criteria.getDesiredTone(),
                tweet.getFullText());
    return prompt;
  }

  private Map<String, Object> buildJsonSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "should_delete",
            Map.of("type", "boolean", "description", "Whether this tweet should be deleted"),
            "reason",
            Map.of(
                "type",
                "string",
                "description",
                "The reason why the tweet should be deleted or retained"),
            "matched_criteria",
            Map.of(
                "type",
                "array",
                "items",
                Map.of("type", "string"),
                "description",
                "List of criteria that were matched for deletion")),
        "required",
        List.of("should_delete", "reason"));
  }

  private GeminiRequest buildRequest(Tweet tweet, AlignmentCriteria criteria) {
    log.debug("Building Gemini request for tweet: {}", tweet.getIdStr());

    String prompt = buildPrompt(tweet, criteria);
    Map<String, Object> schema = buildJsonSchema();

    GeminiPart part = GeminiPart.builder().text(prompt).build();

    GeminiContent content = GeminiContent.builder().parts(List.of(part)).build();

    GeminiGenerationConfig config =
        GeminiGenerationConfig.builder()
            .responseMimeType("application/json")
            .responseJsonSchema(schema)
            .temperature(0.2)
            .build();

    return GeminiRequest.builder().contents(List.of(content)).generationConfig(config).build();
  }

  private GeminiResponse callGeminiApi(GeminiRequest request) {
    log.debug("Calling Gemini API: {}", geminiConfig.getUrl());

    long startTime = System.currentTimeMillis();

    try {
      GeminiResponse response =
          webClient
              .post()
              .uri(geminiConfig.getUrl())
              .header("x-goog-api-key", geminiConfig.getKey())
              .header("Content-Type", "application/json")
              .bodyValue(request)
              .retrieve()
              .bodyToMono(GeminiResponse.class)
              .block();

      long responseTime = System.currentTimeMillis() - startTime;
      rateLimiter.recordSuccess(responseTime);

      return response;

    } catch (WebClientResponseException.TooManyRequests e) {
      rateLimiter.recordRateLimitHit();
      throw e;
    } catch (WebClientResponseException e) {
      if (e.getStatusCode().is5xxServerError()) {
        rateLimiter.recordServerError();
      }
      throw e;
    }
  }

  private TweetEvaluationResult parseResponse(GeminiResponse response, String tweetId)
      throws JsonProcessingException {
    log.debug("Parsing Gemini response for tweet: {}", tweetId);

    if (response.getCandidates() == null || response.getCandidates().isEmpty()) {
      throw new IllegalStateException("No candidates in Gemini response");
    }
    GeminiCandidate candidate = response.getCandidates().get(0);

    GeminiContentResponse content = candidate.getContent();
    if (content.getParts() == null || content.getParts().isEmpty()) {
      throw new IllegalStateException("No parts in candidate content");
    }

    String jsonText = content.getParts().get(0).getText();
    log.debug("Gemini JSON response: {}", jsonText);

    TweetEvaluationResult result = objectMapper.readValue(jsonText, TweetEvaluationResult.class);

    return TweetEvaluationResult.builder()
        .tweetId(tweetId)
        .shouldDelete(result.isShouldDelete())
        .reason(result.getReason())
        .matchedCriteria(result.getMatchedCriteria())
        .build();
  }

  @Retryable(
      retryFor = {
        WebClientResponseException.TooManyRequests.class,
        WebClientResponseException.ServiceUnavailable.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0))
  public TweetEvaluationResult evaluateTweet(Tweet tweet, AlignmentCriteria criteria) {
    log.info("Evaluating tweet: {}", tweet.getIdStr());

    try {
      // Daily quota check: ensure we haven't hit daily limit
      quotaTracker.checkQuota();

      // Adaptive rate limiting: wait before making the API call
      rateLimiter.waitBeforeNextCall();

      GeminiRequest request = buildRequest(tweet, criteria);
      GeminiResponse response = callGeminiApi(request);
      TweetEvaluationResult result = parseResponse(response, tweet.getIdStr());

      // Increment quota count after successful API call
      quotaTracker.incrementRequestCount();

      log.info("Tweet {} evaluation: should_delete={}", tweet.getIdStr(), result.isShouldDelete());

      return result;

    } catch (QuotaExceededException e) {
      log.error("Daily quota exceeded: {}", e.getMessage());
      throw new RuntimeException("Daily quota exceeded: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Rate limiter interrupted for tweet {}", tweet.getIdStr());
      throw new RuntimeException("Rate limiter interrupted: " + tweet.getIdStr(), e);
    } catch (Exception e) {
      log.error("Failed to evaluate tweet {}: {}", tweet.getIdStr(), e.getMessage());
      throw new RuntimeException("Failed to evaluate tweet: " + tweet.getIdStr(), e);
    }
  }
}
