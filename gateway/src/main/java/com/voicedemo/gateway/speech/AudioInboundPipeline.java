package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.ws.MoshiClient;
import org.springframework.stereotype.Component;

@Component
public class AudioInboundPipeline {
    private final MoshiClient moshiClient;

    public AudioInboundPipeline(MoshiClient moshiClient) {
        this.moshiClient = moshiClient;
    }

    public void onPcmFrame(String sessionId, byte[] pcm) {
        moshiClient.sendAudio(sessionId, pcm);
    }
}

