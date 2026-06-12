package com.voicedemo.gateway.session;

public enum SessionStatus {
    IDLE,
    LISTENING,
    MOSHI_TALKING,
    ASK_IN_FLIGHT,
    INJECTING
}
