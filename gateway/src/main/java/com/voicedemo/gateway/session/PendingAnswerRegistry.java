package com.voicedemo.gateway.session;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PendingAnswerRegistry {
    private final ConcurrentMap<String, PendingAnswer> pendingBySession = new ConcurrentHashMap<>();

    public PendingAnswer start(
            String sessionId,
            String correlationId,
            String utteranceId,
            String question,
            int userTurnIndex) {
        PendingAnswer pending = new PendingAnswer(
                sessionId,
                correlationId,
                utteranceId,
                question,
                userTurnIndex,
                PendingAnswerStatus.IN_FLIGHT,
                Instant.now().toEpochMilli()
        );
        pendingBySession.put(sessionId, pending);
        return pending;
    }

    public Optional<PendingAnswer> current(String sessionId) {
        return Optional.ofNullable(pendingBySession.get(sessionId));
    }

    public boolean isCurrent(String sessionId, String correlationId) {
        PendingAnswer pending = pendingBySession.get(sessionId);
        return pending != null && pending.correlationId().equals(correlationId);
    }

    public void markReady(String sessionId, String correlationId) {
        pendingBySession.computeIfPresent(sessionId, (ignored, pending) -> {
            if (!pending.correlationId().equals(correlationId)) {
                return pending;
            }
            return pending.withStatus(PendingAnswerStatus.READY);
        });
    }

    public void clearIfCurrent(String sessionId, String correlationId) {
        PendingAnswer pending = pendingBySession.get(sessionId);
        if (pending != null && pending.correlationId().equals(correlationId)) {
            pendingBySession.remove(sessionId, pending);
        }
    }

    public void clear(String sessionId) {
        pendingBySession.remove(sessionId);
    }

    public record PendingAnswer(
            String sessionId,
            String correlationId,
            String utteranceId,
            String question,
            int userTurnIndexAtDispatch,
            PendingAnswerStatus status,
            long startedAt) {

        PendingAnswer withStatus(PendingAnswerStatus nextStatus) {
            return new PendingAnswer(
                    sessionId,
                    correlationId,
                    utteranceId,
                    question,
                    userTurnIndexAtDispatch,
                    nextStatus,
                    startedAt
            );
        }
    }

    public enum PendingAnswerStatus {
        IN_FLIGHT,
        READY
    }
}
