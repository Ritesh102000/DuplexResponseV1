package com.voicedemo.gateway.session;

public enum SessionStatus {
    IDLE,
    LISTENING,
    ROUTING,
    FAST_THINKING,
    FAST_SPEAKING,
    ASK_PENDING,
    ASK_PENDING_FAST_THINKING,
    ASK_PENDING_FAST_SPEAKING,
    ANSWER_READY,
    BACKEND_INJECTING,
    MOSHI_TALKING,
    ASK_IN_FLIGHT,
    INJECTING
}
