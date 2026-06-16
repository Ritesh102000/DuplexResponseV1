package com.voicedemo.gateway.llm;

public record FastConversationReply(
        FastConversationMode mode,
        String text,
        boolean fallback,
        boolean reasoningFiltered,
        long latencyMs) {
}
