package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.ws.MoshiClient;
import org.springframework.stereotype.Component;

@Component
public class AudioInboundPipeline {
    private final MoshiClient moshiClient;
    private final SttClient sttClient;
    private final ModeProperties properties;

    public AudioInboundPipeline(MoshiClient moshiClient, SttClient sttClient, ModeProperties properties) {
        this.moshiClient = moshiClient;
        this.sttClient = sttClient;
        this.properties = properties;
    }

    public void onPcmFrame(String sessionId, byte[] pcm) {
        if (!"qwen".equalsIgnoreCase(properties.runtime())) {
            moshiClient.sendAudio(sessionId, pcm);
        }
        sttClient.sendAudio(sessionId, pcm);
    }
}
