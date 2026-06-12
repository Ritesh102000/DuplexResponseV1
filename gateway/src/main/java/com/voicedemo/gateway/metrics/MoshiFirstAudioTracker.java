package com.voicedemo.gateway.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MoshiFirstAudioTracker {
    private final EventLogger eventLogger;
    private final ConcurrentMap<String, PendingAskAudio> pendingAsks = new ConcurrentHashMap<>();

    public MoshiFirstAudioTracker(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    public void startAsk(String sessionId, String correlationId, String utteranceId) {
        pendingAsks.put(sessionId, new PendingAskAudio(correlationId, utteranceId));
    }

    public void onMoshiAudio(String sessionId) {
        PendingAskAudio pending = pendingAsks.remove(sessionId);
        if (pending == null) {
            return;
        }
        eventLogger.log("moshi.first_audio", sessionId, Map.of(
                "correlationId", pending.correlationId(),
                "utteranceId", pending.utteranceId()
        ));
    }

    public void clear(String sessionId) {
        pendingAsks.remove(sessionId);
    }

    private record PendingAskAudio(String correlationId, String utteranceId) {
    }
}
