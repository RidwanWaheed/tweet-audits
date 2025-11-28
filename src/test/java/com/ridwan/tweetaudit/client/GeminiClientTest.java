package com.ridwan.tweetaudit.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.config.AlignmentCriteria;
import com.ridwan.tweetaudit.config.GeminiConfig;
import com.ridwan.tweetaudit.dto.*;
import com.ridwan.tweetaudit.model.Tweet;
import com.ridwan.tweetaudit.model.TweetEvaluationResult;
import com.ridwan.tweetaudit.ratelimit.AdaptiveRateLimiter;
import com.ridwan.tweetaudit.ratelimit.DailyQuotaTracker;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private AdaptiveRateLimiter rateLimiter;

    @Mock
    private DailyQuotaTracker quotaTracker;

    private GeminiClient geminiClient;
    private GeminiConfig geminiConfig;
    private AlignmentCriteria alignmentCriteria;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        geminiConfig = new GeminiConfig();
        geminiConfig.setKey("test-api-key");
        geminiConfig.setUrl("https://test-api.com");

        alignmentCriteria = new AlignmentCriteria();
        alignmentCriteria.setForbiddenWords(List.of("kill", "bum"));
        alignmentCriteria.setCheckProfessionalism(true);
        alignmentCriteria.setContext("Test context");
        alignmentCriteria.setDesiredTone("Professional");

        objectMapper = new ObjectMapper();

        // Mock rate limiter to do nothing (tests run instantly)
        doNothing().when(rateLimiter).waitBeforeNextCall();
        doNothing().when(rateLimiter).recordSuccess(anyLong());
        lenient().doNothing().when(rateLimiter).recordRateLimitHit();
        lenient().doNothing().when(rateLimiter).recordServerError();

        // Mock quota tracker to do nothing (tests run without quota checks)
        doNothing().when(quotaTracker).checkQuota();
        doNothing().when(quotaTracker).incrementRequestCount();

        geminiClient = new GeminiClient(objectMapper, geminiConfig, webClient, rateLimiter, quotaTracker);
    }

    @Test
    void shouldSuccessfullyEvaluateTweetAndReturnFlagged() {
        Tweet tweet = Tweet.builder()
                .idStr("12345")
                .fullText("This is killing me!")
                .createdAt("2024-01-01")
                .build();

        String mockJsonResponse = """
            {
              "should_delete": true,
              "reason": "Contains forbidden word 'kill'",
              "matched_criteria": ["forbidden_words"]
            }
            """;

        GeminiResponse mockResponse = createMockGeminiResponse(mockJsonResponse);

        setupWebClientMock(mockResponse);

        TweetEvaluationResult result = geminiClient.evaluateTweet(tweet, alignmentCriteria);

        assertNotNull(result);
        assertEquals("12345", result.getTweetId());
        assertTrue(result.isShouldDelete());
        assertEquals("Contains forbidden word 'kill'", result.getReason());
        assertEquals(1, result.getMatchedCriteria().size());
        assertEquals("forbidden_words", result.getMatchedCriteria().get(0));
        assertNull(result.getErrorMessage());

        verify(webClient).post();
        verify(requestBodyUriSpec).uri(geminiConfig.getUrl());
    }

    @Test
    void shouldSuccessfullyEvaluateTweetAndReturnClean() {
        Tweet tweet = Tweet.builder()
                .idStr("67890")
                .fullText("Just finished a great coding session!")
                .createdAt("2024-01-01")
                .build();

        String mockJsonResponse = """
            {
              "should_delete": false,
              "reason": "Tweet is professional and appropriate",
              "matched_criteria": []
            }
            """;

        GeminiResponse mockResponse = createMockGeminiResponse(mockJsonResponse);

        setupWebClientMock(mockResponse);

        TweetEvaluationResult result = geminiClient.evaluateTweet(tweet, alignmentCriteria);

        assertNotNull(result);
        assertEquals("67890", result.getTweetId());
        assertFalse(result.isShouldDelete());
        assertEquals("Tweet is professional and appropriate", result.getReason());
        assertTrue(result.getMatchedCriteria().isEmpty());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldHandleMultipleMatchedCriteria() {
        Tweet tweet = Tweet.builder()
                .idStr("11111")
                .fullText("That bum is killing me with stupid questions")
                .createdAt("2024-01-01")
                .build();

        String mockJsonResponse = """
            {
              "should_delete": true,
              "reason": "Multiple forbidden words detected",
              "matched_criteria": ["forbidden_words", "unprofessional", "tone"]
            }
            """;

        GeminiResponse mockResponse = createMockGeminiResponse(mockJsonResponse);

        setupWebClientMock(mockResponse);

        TweetEvaluationResult result = geminiClient.evaluateTweet(tweet, alignmentCriteria);

        assertNotNull(result);
        assertTrue(result.isShouldDelete());
        assertEquals(3, result.getMatchedCriteria().size());
        assertTrue(result.getMatchedCriteria().contains("forbidden_words"));
        assertTrue(result.getMatchedCriteria().contains("unprofessional"));
        assertTrue(result.getMatchedCriteria().contains("tone"));
    }

    private void setupWebClientMock(GeminiResponse mockResponse) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(GeminiResponse.class)).thenReturn(Mono.just(mockResponse));
    }

    private GeminiResponse createMockGeminiResponse(String jsonContent) {
        GeminiPart part = GeminiPart.builder()
                .text(jsonContent)
                .build();

        GeminiContentResponse content = GeminiContentResponse.builder()
                .parts(List.of(part))
                .role("model")
                .build();

        GeminiCandidate candidate = GeminiCandidate.builder()
                .content(content)
                .finishReason("STOP")
                .index(0)
                .build();

        return GeminiResponse.builder()
                .candidates(List.of(candidate))
                .build();
    }
}
