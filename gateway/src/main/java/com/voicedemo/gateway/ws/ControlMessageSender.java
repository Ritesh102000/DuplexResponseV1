package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ControlMessageSender {
    private final ObjectMapper objectMapper;

    public ControlMessageSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sessionStart(WebSocketSession session, String sessionId) {
        send(session, Map.of(
                "type", "session.start",
                "sessionId", sessionId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void moshiTranscript(WebSocketSession session, String sessionId, String text) {
        send(session, Map.of(
                "type", "transcript.moshi",
                "sessionId", sessionId,
                "text", text,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void error(WebSocketSession session, String code, String message) {
        send(session, Map.of(
                "type", "error",
                "code", code,
                "message", message,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void send(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(toJson(payload)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to send control message", e);
        }
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}

