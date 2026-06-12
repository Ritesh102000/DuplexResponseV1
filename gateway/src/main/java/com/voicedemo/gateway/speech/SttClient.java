package com.voicedemo.gateway.speech;

public interface SttClient {
    void connect(String sessionId, SttCallbacks callbacks);

    void sendAudio(String sessionId, byte[] pcm);

    void submitUtterance(String sessionId, String text, long endTs);

    void disconnect(String sessionId);
}

