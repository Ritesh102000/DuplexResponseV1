package com.voicedemo.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class SidecarHealthVerifier implements ApplicationRunner {
    private final ModeProperties properties;
    private final WebClient.Builder webClientBuilder;

    public SidecarHealthVerifier(ModeProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("real".equalsIgnoreCase(properties.sttMode())) {
            verify("STT", properties.sttUrl());
        }
        if ("real".equalsIgnoreCase(properties.ttsMode())) {
            verify("TTS", properties.ttsUrl());
        }
    }

    private void verify(String name, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(name + " sidecar URL is required in real mode");
        }
        RuntimeException lastFailure = null;
        int attempts = Math.max(1, properties.sidecarStartupRetries());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = webClientBuilder.baseUrl(baseUrl).build()
                        .get()
                        .uri("/health")
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(Duration.ofMillis(Math.max(250, properties.sidecarHealthTimeoutMs())));
                if (response != null && "UP".equalsIgnoreCase(response.path("status").asText())) {
                    return;
                }
                lastFailure = new IllegalStateException(name + " sidecar returned unhealthy response");
            } catch (RuntimeException e) {
                lastFailure = e;
            }
            sleepBeforeRetry(attempt, attempts);
        }
        throw new IllegalStateException(name + " sidecar health check failed at " + baseUrl, lastFailure);
    }

    private void sleepBeforeRetry(int attempt, int attempts) {
        if (attempt >= attempts) {
            return;
        }
        try {
            Thread.sleep(Math.max(0, properties.sidecarStartupBackoffMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for sidecar startup", e);
        }
    }
}
