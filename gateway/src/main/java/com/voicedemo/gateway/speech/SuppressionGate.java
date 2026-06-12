package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.session.SessionStatus;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SuppressionGate {
    private final EventLogger eventLogger;
    private final int tokenThreshold;
    private final int fadeSamples;
    private final ConcurrentMap<String, GateState> sessions = new ConcurrentHashMap<>();

    public SuppressionGate(EventLogger eventLogger, ModeProperties properties) {
        this.eventLogger = eventLogger;
        this.tokenThreshold = properties.suppressionTokenThreshold();
        this.fadeSamples = Math.max(1, PcmFrames.SAMPLE_RATE * properties.suppressionFadeMs() / 1000);
    }

    public void startAsk(String sessionId, String correlationId) {
        sessions.put(sessionId, new GateState(correlationId, fadeSamples));
    }

    public void endAsk(String sessionId) {
        sessions.remove(sessionId);
    }

    public void reset(String sessionId) {
        sessions.remove(sessionId);
    }

    public void observeMoshiText(String sessionId, SessionStatus status, String text) {
        if (status != SessionStatus.ASK_IN_FLIGHT) {
            return;
        }
        GateState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        int tokenCount = countTokens(text);
        if (tokenCount == 0) {
            return;
        }
        SuppressionDecision decision = state.addTokens(tokenCount, tokenThreshold);
        if (decision.fadedNow()) {
            eventLogger.log("suppression.faded", sessionId, Map.of(
                    "correlationId", decision.correlationId(),
                    "tokenCount", decision.totalTokens(),
                    "threshold", tokenThreshold
            ));
        }
    }

    public byte[] filterMoshiAudio(String sessionId, SessionStatus status, byte[] pcm) {
        if (status != SessionStatus.ASK_IN_FLIGHT) {
            return pcm;
        }
        GateState state = sessions.get(sessionId);
        if (state == null || !state.isSuppressed()) {
            return pcm;
        }
        return state.fadeOrSilence(pcm);
    }

    private int countTokens(String text) {
        if (text == null) {
            return 0;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9']+", " ")
                .trim();
        if (normalized.isBlank()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    private static final class GateState {
        private final String correlationId;
        private final int fadeSamples;
        private int tokenCount;
        private boolean suppressed;
        private int remainingFadeSamples;

        private GateState(String correlationId, int fadeSamples) {
            this.correlationId = correlationId;
            this.fadeSamples = fadeSamples;
            this.remainingFadeSamples = fadeSamples;
        }

        synchronized SuppressionDecision addTokens(int addedTokens, int tokenThreshold) {
            tokenCount += addedTokens;
            if (!suppressed && tokenCount > tokenThreshold) {
                suppressed = true;
                return new SuppressionDecision(correlationId, tokenCount, true);
            }
            return new SuppressionDecision(correlationId, tokenCount, false);
        }

        synchronized boolean isSuppressed() {
            return suppressed;
        }

        synchronized byte[] fadeOrSilence(byte[] pcm) {
            if (remainingFadeSamples <= 0) {
                return new byte[pcm.length];
            }
            byte[] faded = new byte[pcm.length];
            ByteBuffer input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer output = ByteBuffer.wrap(faded).order(ByteOrder.LITTLE_ENDIAN);
            while (input.remaining() >= Short.BYTES) {
                short sample = input.getShort();
                double gain = Math.max(0.0, remainingFadeSamples / (double) fadeSamples);
                output.putShort((short) Math.round(sample * gain));
                remainingFadeSamples--;
            }
            while (output.hasRemaining()) {
                output.put((byte) 0);
            }
            return faded;
        }
    }

    private record SuppressionDecision(String correlationId, int totalTokens, boolean fadedNow) {
    }
}
