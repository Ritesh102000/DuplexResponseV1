package com.voicedemo.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.llmApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(2));
        if (response == null) {
            throw new IllegalStateException("empty LLM response");
        }
        return response.at("/choices/0/message/content").asText();
    }
}

