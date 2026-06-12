package com.voicedemo.gateway.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "voice.stt-mode", havingValue = "stub", matchIfMissing = true)
public class StubSttClient implements SttClient {
    private final Map<String, SttCallbacks> callbacks = new ConcurrentHashMap<>();

    @Override
    public void connect(String sessionId, SttCallbacks callbacks) {
        this.callbacks.put(sessionId, callbacks);
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcm) {
        // Stub mode uses explicit transcript.user JSON messages from tests/browser.
    }

    @Override
    public void submitUtterance(String sessionId, String text, long endTs) {
        SttCallbacks callback = callbacks.get(sessionId);
        if (callback != null) {
            callback.onUtterance(text, endTs);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        callbacks.remove(sessionId);
    }
}

