package com.voicedemo.gateway.llm;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.jobs.AskJobRequest;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AnswerService {
    private final LlmClient llmClient;
    private final ModeProperties properties;
    private final PromptLibrary prompts;
    private final EventLogger eventLogger;

    public AnswerService(
            LlmClient llmClient,
            ModeProperties properties,
            PromptLibrary prompts,
            EventLogger eventLogger) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.prompts = prompts;
        this.eventLogger = eventLogger;
    }

    public String answer(AskJobRequest request) {
        List<Msg> messages = List.of(
                new Msg("system", prompts.answerStyle()),
                new Msg("user", """
                        Recent transcript:
                        %s

                        Latest user question:
                        %s
                        """.formatted(formatTranscript(request.transcriptSnapshot()), request.question()))
        );
        eventLogger.log("llm.answer.request", request.sessionId(), Map.of(
                "correlationId", request.correlationId(),
                "utteranceId", request.utteranceId(),
                "model", properties.llmModelAnswer(),
                "messages", messagesPayload(messages)
        ));
        long startedAt = Instant.now().toEpochMilli();
        try {
            String response = llmClient.chat(properties.llmModelAnswer(), messages, 0.2, 220);
            eventLogger.log("llm.answer.response", request.sessionId(), Map.of(
                    "correlationId", request.correlationId(),
                    "utteranceId", request.utteranceId(),
                    "model", properties.llmModelAnswer(),
                    "latencyMs", Instant.now().toEpochMilli() - startedAt,
                    "text", response
            ));
            return response;
        } catch (RuntimeException e) {
            eventLogger.log("llm.answer.error", request.sessionId(), Map.of(
                    "correlationId", request.correlationId(),
                    "utteranceId", request.utteranceId(),
                    "latencyMs", Instant.now().toEpochMilli() - startedAt,
                    "error", e.getClass().getSimpleName()
            ));
            throw e;
        }
    }

    private String formatTranscript(List<TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase() + ": " + line.text())
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
}
