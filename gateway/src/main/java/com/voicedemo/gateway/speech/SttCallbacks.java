package com.voicedemo.gateway.speech;

public interface SttCallbacks {
    void onUtterance(String text, long endTs);
}

