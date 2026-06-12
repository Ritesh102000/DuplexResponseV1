package com.voicedemo.gateway.ws;

public interface MoshiCallbacks {
    void onOpen();

    void onAudio(byte[] pcm);

    void onText(String text);

    void onClose();

    void onError(Throwable error);
}

