package com.voicedemo.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "voice.llm-mode", havingValue = "real")
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final ModeProperties properties;
    private final WebClient webClient;

    public OpenAiCompatibleLlmClient(ModeProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder.baseUrl(properties.llmBaseUrl()).build();
    }

    @Override
    public String chat(String model, List<Msg> messages, double temp, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temp,
                "max_tokens", maxTokens,
                "messages", messages
        );
        RuntimeException lastFailure = null;
        int attempts = Math.max(1, properties.llmMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode response = webClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.llmApiKey())
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(Duration.ofMillis(properties.askTimeoutMs()));
                if (response == null) {
                    throw new IllegalStateException("empty LLM response");
                }
                return response.at("/choices/0/message/content").asText();
            } catch (RuntimeException e) {
                lastFailure = e;
                if (!isRetryable(e) || attempt == attempts) {
                    break;
                }
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("LLM chat failed after " + attempts + " attempt(s)", lastFailure);
    }

    private boolean isRetryable(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 429 || status >= 500;
            }
            current = current.getCause();
        }
        return true;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(Math.max(0, properties.llmRetryBackoffMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry LLM call", e);
        }
    }
}
