package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.ws.MoshiClient;
import org.springframework.stereotype.Component;

@Component
public class AudioInboundPipeline {
    private final MoshiClient moshiClient;
    private final SttClient sttClient;

    public AudioInboundPipeline(MoshiClient moshiClient, SttClient sttClient) {
        this.moshiClient = moshiClient;
        this.sttClient = sttClient;
    }

    public void onPcmFrame(String sessionId, byte[] pcm) {
        moshiClient.sendAudio(sessionId, pcm);
        sttClient.sendAudio(sessionId, pcm);
    }
}
