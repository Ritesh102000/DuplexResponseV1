package com.voicedemo.gateway.llm;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LlmHarmonizer implements Harmonizer {
    private final LlmClient llmClient;
    private final ModeProperties properties;
    private final PromptLibrary prompts;

    public LlmHarmonizer(LlmClient llmClient, ModeProperties properties, PromptLibrary prompts) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.prompts = prompts;
    }

    @Override
    public String harmonize(String rawAnswer, List<TranscriptLine> recent, boolean reintroduce) {
        return llmClient.chat(
                properties.llmModelAnswer(),
                List.of(
                        new Msg("system", prompts.harmonizer()),
                        new Msg("user", """
                                REINTRODUCE=%s

                                Recent transcript:
                                %s

                                Raw answer:
                                %s
                                """.formatted(reintroduce, formatTranscript(recent), rawAnswer))
                ),
                0.2,
                120
        );
    }

    private String formatTranscript(List<TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase() + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }
}
