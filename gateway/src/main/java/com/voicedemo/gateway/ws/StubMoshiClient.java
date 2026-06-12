package com.voicedemo.gateway.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "voice.moshi-mode", havingValue = "stub", matchIfMissing = true)
public class StubMoshiClient implements MoshiClient {
    public static final byte[] DROP_FRAME = "__drop__".getBytes(StandardCharsets.UTF_8);
    public static final String TEXT_FRAME_PREFIX = "__moshi_text__:";

    private final Map<String, MoshiCallbacks> sessions = new ConcurrentHashMap<>();

    @Override
    public void connect(String sessionId, MoshiCallbacks callbacks) {
        sessions.put(sessionId, callbacks);
        callbacks.onOpen();
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcm) {
        MoshiCallbacks callbacks = sessions.get(sessionId);
        if (callbacks == null) {
            return;
        }
        if (matchesDropFrame(pcm)) {
            sessions.remove(sessionId);
            callbacks.onClose();
            return;
        }
        String text = fixtureText(pcm);
        if (text != null) {
            callbacks.onText(text);
            return;
        }
        callbacks.onAudio(pcm);
    }

    @Override
    public void disconnect(String sessionId) {
        sessions.remove(sessionId);
    }

    private boolean matchesDropFrame(byte[] pcm) {
        if (pcm.length != DROP_FRAME.length) {
            return false;
        }
        for (int i = 0; i < pcm.length; i++) {
            if (pcm[i] != DROP_FRAME[i]) {
                return false;
            }
        }
        return true;
    }

    private String fixtureText(byte[] pcm) {
        String text = new String(pcm, StandardCharsets.UTF_8);
        if (!text.startsWith(TEXT_FRAME_PREFIX)) {
            return null;
        }
        return text.substring(TEXT_FRAME_PREFIX.length());
    }
}
