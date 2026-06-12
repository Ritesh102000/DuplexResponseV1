package com.voicedemo.gateway.jobs;

public record AskJobResult(
        AskJobResultType type,
        String sessionId,
        String correlationId,
        String utteranceId,
        String text,
        boolean reintroduced) {

    public static AskJobResult inject(AskJobRequest request, String text, boolean reintroduced) {
        return new AskJobResult(
                AskJobResultType.INJECT,
                request.sessionId(),
                request.correlationId(),
                request.utteranceId(),
                text,
                reintroduced
        );
    }

    public static AskJobResult dropped(AskJobRequest request) {
        return new AskJobResult(
                AskJobResultType.DROPPED,
                request.sessionId(),
                request.correlationId(),
                request.utteranceId(),
                null,
                false
        );
    }
}
