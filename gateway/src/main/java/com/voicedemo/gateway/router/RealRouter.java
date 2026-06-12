package com.voicedemo.gateway.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.llm.LlmClient;
import com.voicedemo.gateway.llm.Msg;
import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "voice.llm-mode", havingValue = "real")
public class RealRouter implements RouterService {
    private final LlmClient llmClient;
    private final ModeProperties properties;
    private final ObjectMapper objectMapper;
    private final HeuristicFallback fallback = new HeuristicFallback();

    public RealRouter(LlmClient llmClient, ModeProperties properties, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RouteDecision classify(List<TranscriptLine> window, String utterance) {
        try {
            String result = CompletableFuture.supplyAsync(() -> llmClient.chat(
                    properties.llmModelRouter(),
                    List.of(
                            new Msg("system", """
                                    Classify the latest user utterance for a voice assistant.
                                    Return strict JSON only: {"label":"CHAT|ASK|ACT","confidence":0..1}.
                                    CHAT is casual conversation. ASK needs factual/reasoned answer. ACT asks the assistant to perform an action.
                                    """),
                            new Msg("user", "Recent transcript:\n" + window + "\nLatest utterance: " + utterance)
                    ),
                    0.0,
                    80
            )).get(Duration.ofMillis(1500).toMillis(), TimeUnit.MILLISECONDS);
            JsonNode json = objectMapper.readTree(result);
            return new RouteDecision(
                    RouteLabel.valueOf(json.get("label").asText()),
                    json.path("confidence").asDouble(0.75),
                    "llm"
            );
        } catch (Exception ignored) {
            return fallback.classify(window, utterance);
        }
    }

    private static final class HeuristicFallback extends HeuristicRouter {
    }
}

