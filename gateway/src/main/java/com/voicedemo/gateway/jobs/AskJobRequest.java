package com.voicedemo.gateway.jobs;

import com.voicedemo.gateway.transcript.TranscriptLine;

import java.util.List;

public record AskJobRequest(
        String correlationId,
        String sessionId,
        String utteranceId,
        String question,
        List<TranscriptLine> transcriptSnapshot,
        int userTurnIndexAtDispatch,
        long dispatchedAt) {
}
