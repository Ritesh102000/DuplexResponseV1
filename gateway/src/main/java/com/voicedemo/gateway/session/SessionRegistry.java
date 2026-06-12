package com.voicedemo.gateway.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class SessionRegistry {
    private final Cache<String, SessionState> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(1_000)
            .build();

    public SessionState create(String requestedSessionId) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedSessionId;
        SessionState state = new SessionState(sessionId);
        sessions.put(sessionId, state);
        return state;
    }

    public void remove(String sessionId) {
        sessions.invalidate(sessionId);
    }
}

