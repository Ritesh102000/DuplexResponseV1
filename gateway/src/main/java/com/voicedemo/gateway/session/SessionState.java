package com.voicedemo.gateway.session;

public class SessionState {
    private final String sessionId;
    private SessionStatus status = SessionStatus.IDLE;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized SessionStatus status() {
        return status;
    }

    public synchronized void markListening() {
        status = SessionStatus.LISTENING;
    }

    public synchronized void markIdle() {
        status = SessionStatus.IDLE;
    }
}

