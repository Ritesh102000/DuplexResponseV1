package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "voice.stt-mode", havingValue = "real")
public class RealSttClient implements SttClient {
    public RealSttClient(ModeProperties properties) {
    }

    @Override
    public void connect(String sessionId, SttCallbacks callbacks) {
        // Phase 2 sidecar contract is scaffolded; streaming HTTP integration can be refined
        // once real STT runtime is exercised outside CI.
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcm) {
    }

    @Override
    public void submitUtterance(String sessionId, String text, long endTs) {
    }

    @Override
    public void disconnect(String sessionId) {
    }
}

