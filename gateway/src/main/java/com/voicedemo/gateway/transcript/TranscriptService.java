package com.voicedemo.gateway.transcript;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TranscriptService {
    private final Map<String, TranscriptBuffer> buffers = new ConcurrentHashMap<>();

    public TranscriptLine addUserUtterance(String sessionId, String text, long ts) {
        TranscriptLine line = new TranscriptLine(Speaker.USER, text, "u-" + UUID.randomUUID(), ts);
        buffer(sessionId).add(line);
        return line;
    }

    public TranscriptLine addMoshiText(String sessionId, String text) {
        TranscriptLine line = new TranscriptLine(Speaker.MOSHI, text, null, Instant.now().toEpochMilli());
        buffer(sessionId).add(line);
        return line;
    }

    public List<TranscriptLine> recent(String sessionId, int limit) {
        return buffer(sessionId).recent(limit);
    }

    private TranscriptBuffer buffer(String sessionId) {
        return buffers.computeIfAbsent(sessionId, ignored -> new TranscriptBuffer(80));
    }
}

