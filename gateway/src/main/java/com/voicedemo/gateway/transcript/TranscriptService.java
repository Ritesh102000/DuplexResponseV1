package com.voicedemo.gateway.transcript;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TranscriptService {
    private final Map<String, TranscriptBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userTurnIndexes = new ConcurrentHashMap<>();

    public TranscriptLine addUserUtterance(String sessionId, String text, long ts) {
        userTurnIndexes.computeIfAbsent(sessionId, ignored -> new AtomicInteger()).incrementAndGet();
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

    public int userTurnIndex(String sessionId) {
        return userTurnIndexes.getOrDefault(sessionId, new AtomicInteger()).get();
    }

    public void remove(String sessionId) {
        buffers.remove(sessionId);
        userTurnIndexes.remove(sessionId);
    }

    private TranscriptBuffer buffer(String sessionId) {
        return buffers.computeIfAbsent(sessionId, ignored -> new TranscriptBuffer(80));
    }
}
