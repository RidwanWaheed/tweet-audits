package com.ridwan.tweetaudit.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.lang.NonNull;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
public class WebClientConfig {

  @Bean
  @SuppressWarnings("null")
  public WebClient webClient() {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // What value?
            .responseTimeout(Duration.ofSeconds(30)) // What value?
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .filter(logRequest())
        .filter(logResponse())
        .build();
  }

  @NonNull
  private ExchangeFilterFunction logRequest() {
    return ExchangeFilterFunction.ofRequestProcessor(
        clientRequest -> {
          log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
          return Mono.just(clientRequest);
        });
  }

  @NonNull
  private ExchangeFilterFunction logResponse() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          log.debug("Response Status: {}", clientResponse.statusCode());
          return Mono.just(clientResponse);
        });
  }
}
