package com.voicedemo.gateway.llm;

import com.voicedemo.gateway.transcript.TranscriptLine;

import java.util.List;

public record FastConversationRequest(
        FastConversationMode mode,
        String sessionId,
        String utteranceId,
        String correlationId,
        String latestUserText,
        String pendingQuestion,
        List<TranscriptLine> transcriptWindow) {
}
