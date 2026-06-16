package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicedemo.gateway.router.RouteDecision;
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

    public void fastTranscript(WebSocketSession session, String sessionId, String text) {
        send(session, Map.of(
                "type", "transcript.fast",
                "sessionId", sessionId,
                "text", text,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void backendTranscript(WebSocketSession session, String sessionId, String text) {
        send(session, Map.of(
                "type", "transcript.backend",
                "sessionId", sessionId,
                "text", text,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void userTranscript(WebSocketSession session, String sessionId, String utteranceId, String text, long ts) {
        send(session, Map.of(
                "type", "transcript.user",
                "sessionId", sessionId,
                "utteranceId", utteranceId,
                "text", text,
                "ts", ts
        ));
    }

    public void routerDecision(WebSocketSession session, String sessionId, String utteranceId, RouteDecision decision) {
        routerDecision(session, sessionId, utteranceId, decision, null);
    }

    public void routerDecision(
            WebSocketSession session,
            String sessionId,
            String utteranceId,
            RouteDecision decision,
            String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "router.decision");
        payload.put("sessionId", sessionId);
        payload.put("utteranceId", utteranceId);
        payload.put("label", decision.label().name());
        payload.put("confidence", decision.confidence());
        if (correlationId != null) {
            payload.put("correlationId", correlationId);
        }
        payload.put("ts", Instant.now().toEpochMilli());
        send(session, payload);
    }

    public void injectStart(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "inject.start",
                "sessionId", sessionId,
                "correlationId", correlationId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void injectEnd(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "inject.end",
                "sessionId", sessionId,
                "correlationId", correlationId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void fastReplyStart(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "fast.reply.start",
                "sessionId", sessionId,
                "correlationId", correlationId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void fastReplyEnd(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "fast.reply.end",
                "sessionId", sessionId,
                "correlationId", correlationId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void backendInjectStart(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "backend.inject.start",
                "sessionId", sessionId,
                "correlationId", correlationId,
                "ts", Instant.now().toEpochMilli()
        ));
    }

    public void backendInjectEnd(WebSocketSession session, String sessionId, String correlationId) {
        send(session, Map.of(
                "type", "backend.inject.end",
                "sessionId", sessionId,
                "correlationId", correlationId,
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
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}
