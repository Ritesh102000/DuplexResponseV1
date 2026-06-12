package com.voicedemo.gateway.llm;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.jobs.AskJobRequest;
import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AnswerService {
    private final LlmClient llmClient;
    private final ModeProperties properties;
    private final PromptLibrary prompts;

    public AnswerService(LlmClient llmClient, ModeProperties properties, PromptLibrary prompts) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.prompts = prompts;
    }

    public String answer(AskJobRequest request) {
        return llmClient.chat(
                properties.llmModelAnswer(),
                List.of(
                        new Msg("system", prompts.answerStyle()),
                        new Msg("user", """
                                Recent transcript:
                                %s

                                Latest user question:
                                %s
                                """.formatted(formatTranscript(request.transcriptSnapshot()), request.question()))
                ),
                0.2,
                220
        );
    }

    private String formatTranscript(List<TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase() + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }
}
