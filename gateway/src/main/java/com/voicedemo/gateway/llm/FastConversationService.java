package com.voicedemo.gateway.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FastConversationService {
    private static final String CHAT_FALLBACK = "I hear you.";
    private static final String PENDING_FALLBACK = "Good question. I'm checking that carefully.";
    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern THINK_PREFIX = Pattern.compile("(?is)^.*?</think>");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private final ModeProperties properties;
    private final PromptLibrary prompts;
    private final EventLogger eventLogger;
    private final WebClient.Builder webClientBuilder;
    private final AskPendingPromptSanitizer askPendingPromptSanitizer;

    public FastConversationService(
            ModeProperties properties,
            PromptLibrary prompts,
            EventLogger eventLogger,
            WebClient.Builder webClientBuilder,
            AskPendingPromptSanitizer askPendingPromptSanitizer) {
        this.properties = properties;
        this.prompts = prompts;
        this.eventLogger = eventLogger;
        this.webClientBuilder = webClientBuilder;
        this.askPendingPromptSanitizer = askPendingPromptSanitizer;
    }

    public FastConversationReply reply(FastConversationRequest request) {
        long startedAt = Instant.now().toEpochMilli();
        List<Msg> messages = messagesFor(request);
        eventLogger.log("fast.reply.request", request.sessionId(), eventPayload(request, Map.of(
                "promptMode", request.mode().name(),
                "model", properties.fastLlmModel(),
                "messages", messagesPayload(messages)
        )));

        if (!"real".equalsIgnoreCase(properties.fastLlmMode())) {
            String text = stubReply(request);
            long latencyMs = Instant.now().toEpochMilli() - startedAt;
            eventLogger.log("fast.reply.response", request.sessionId(), eventPayload(request, Map.of(
                    "promptMode", request.mode().name(),
                    "model", "stub",
                    "latencyMs", latencyMs,
                    "text", text,
                    "fallback", false,
                    "reasoningFiltered", false
            )));
            return new FastConversationReply(request.mode(), text, false, false, latencyMs);
        }

        try {
            JsonNode response = callFastModel(messages);
            JsonNode message = response.at("/choices/0/message");
            String rawContent = message.path("content").asText("");
            boolean reasoningPresent = message.hasNonNull("reasoning")
                    || message.hasNonNull("reasoning_content")
                    || response.hasNonNull("thinking");
            SanitizedText sanitized = sanitizeForSpeech(rawContent, request.mode());
            long latencyMs = Instant.now().toEpochMilli() - startedAt;
            eventLogger.log("fast.reply.response", request.sessionId(), eventPayload(request, Map.of(
                    "promptMode", request.mode().name(),
                    "model", properties.fastLlmModel(),
                    "latencyMs", latencyMs,
                    "text", sanitized.text(),
                    "fallback", sanitized.fallback(),
                    "reasoningPresent", reasoningPresent,
                    "reasoningFiltered", sanitized.reasoningFiltered()
            )));
            return new FastConversationReply(
                    request.mode(),
                    sanitized.text(),
                    sanitized.fallback(),
                    sanitized.reasoningFiltered() || reasoningPresent,
                    latencyMs
            );
        } catch (RuntimeException e) {
            String fallback = fallback(request.mode());
            long latencyMs = Instant.now().toEpochMilli() - startedAt;
            eventLogger.log("fast.reply.error", request.sessionId(), eventPayload(request, Map.of(
                    "promptMode", request.mode().name(),
                    "model", properties.fastLlmModel(),
                    "latencyMs", latencyMs,
                    "error", e.getClass().getSimpleName(),
                    "fallbackText", fallback
            )));
            return new FastConversationReply(request.mode(), fallback, true, false, latencyMs);
        }
    }

    private JsonNode callFastModel(List<Msg> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.fastLlmModel());
        body.put("temperature", properties.fastReplyTemperature());
        body.put("max_tokens", properties.fastReplyMaxTokens());
        body.put("messages", messages);

        WebClient.RequestBodySpec request = webClientBuilder
                .baseUrl(properties.fastLlmBaseUrl())
                .build()
                .post()
                .uri("/chat/completions");
        if (properties.fastLlmApiKey() != null && !properties.fastLlmApiKey().isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.fastLlmApiKey());
        }
        JsonNode response = request
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofMillis(Math.max(1000, properties.fastStaleUtteranceMs())));
        if (response == null) {
            throw new IllegalStateException("empty fast LLM response");
        }
        return response;
    }

    private List<Msg> messagesFor(FastConversationRequest request) {
        if (request.mode() == FastConversationMode.ASK_PENDING) {
            long sanitizerStartedAt = Instant.now().toEpochMilli();
            AskPendingPromptSanitizer.SanitizedAskPrompt sanitizedPrompt = askPendingPromptSanitizer.sanitize(
                    request.pendingQuestion(),
                    request.latestUserText(),
                    properties.fastPendingMaxWords()
            );
            eventLogger.log("fast.prompt.sanitized", request.sessionId(), eventPayload(request, Map.of(
                    "promptMode", request.mode().name(),
                    "latencyMs", Instant.now().toEpochMilli() - sanitizerStartedAt,
                    "state", sanitizedPrompt.state(),
                    "sanitizedContext", sanitizedPrompt.sanitizedContext(),
                    "latestUserIntent", sanitizedPrompt.latestUserIntent(),
                    "rules", sanitizedPrompt.rules()
            )));
            return List.of(
                    new Msg("system", prompts.fastAskPending()),
                    new Msg("user", sanitizedPrompt.promptText())
            );
        }
        return List.of(
                new Msg("system", prompts.fastChat()),
                new Msg("user", """
                        Recent transcript:
                        %s

                        Latest user utterance:
                        %s
                        """.formatted(formatTranscript(request.transcriptWindow()), safe(request.latestUserText())))
        );
    }

    private SanitizedText sanitizeForSpeech(String rawContent, FastConversationMode mode) {
        String fallback = fallback(mode);
        if (rawContent == null || rawContent.isBlank()) {
            return new SanitizedText(fallback, true, false);
        }
        String text = THINK_BLOCK.matcher(rawContent).replaceAll(" ");
        text = THINK_PREFIX.matcher(text).replaceFirst(" ");
        boolean reasoningFiltered = !text.equals(rawContent);
        text = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (looksLikeReasoning(text)) {
            return new SanitizedText(fallback, true, true);
        }
        text = firstSentence(text);
        text = stripDecorations(text);
        if (mode == FastConversationMode.ASK_PENDING) {
            text = limitWords(text, Math.max(3, properties.fastPendingMaxWords()));
        }
        if (text.isBlank() || looksLikeReasoning(text)) {
            return new SanitizedText(fallback, true, true);
        }
        return new SanitizedText(text, false, reasoningFiltered);
    }

    private boolean looksLikeReasoning(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.startsWith("okay, the user")
                || normalized.startsWith("the user")
                || normalized.startsWith("i need to")
                || normalized.startsWith("let me")
                || normalized.contains("instruction says")
                || normalized.contains("system prompt")
                || normalized.contains("word count")
                || normalized.contains("i should");
    }

    private String firstSentence(String text) {
        String[] sentences = SENTENCE_END.split(text, 2);
        return sentences.length == 0 ? text : sentences[0].trim();
    }

    private String stripDecorations(String text) {
        return text.replaceAll("^[\"'`]+|[\"'`]+$", "")
                .replaceAll("^assistant:\\s*", "")
                .trim();
    }

    private String limitWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            return text;
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < maxWords; i++) {
            kept.add(words[i]);
        }
        String result = String.join(" ", kept).replaceAll("[,;:]$", "");
        return result.endsWith(".") || result.endsWith("!") || result.endsWith("?") ? result : result + ".";
    }

    private String stubReply(FastConversationRequest request) {
        if (request.mode() == FastConversationMode.ASK_PENDING) {
            return PENDING_FALLBACK;
        }
        String text = safe(request.latestUserText()).toLowerCase(Locale.ROOT);
        if (text.contains("hello") || text.contains("hi")) {
            return "Hey, I'm listening.";
        }
        return CHAT_FALLBACK;
    }

    private String fallback(FastConversationMode mode) {
        return mode == FastConversationMode.ASK_PENDING ? PENDING_FALLBACK : CHAT_FALLBACK;
    }

    private String formatTranscript(List<TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase(Locale.ROOT) + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }

    private List<Map<String, String>> messagesPayload(List<Msg> messages) {
        return messages.stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content()
                ))
                .toList();
    }

    private Map<String, Object> eventPayload(FastConversationRequest request, Map<String, ?> attributes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("utteranceId", request.utteranceId());
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            payload.put("correlationId", request.correlationId());
        }
        payload.putAll(attributes);
        return payload;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record SanitizedText(String text, boolean fallback, boolean reasoningFiltered) {
    }
}
