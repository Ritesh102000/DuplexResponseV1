package com.voicedemo.gateway.ws;

public interface MoshiClient {
    void connect(String sessionId, MoshiCallbacks callbacks);

    void sendAudio(String sessionId, byte[] pcm);

    void disconnect(String sessionId);
}

