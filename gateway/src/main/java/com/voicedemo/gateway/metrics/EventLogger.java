package com.voicedemo.gateway.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EventLogger {
    private final ObjectMapper objectMapper;
    private final Path path;

    public EventLogger(ObjectMapper objectMapper, ModeProperties properties) {
        this.objectMapper = objectMapper;
        this.path = Path.of(properties.eventLogPath());
    }

    public synchronized void log(String event, String sessionId, Map<String, Object> attributes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("sessionId", sessionId);
        payload.put("ts", Instant.now().toEpochMilli());
        payload.putAll(attributes);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    objectMapper.writeValueAsString(payload) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to append event log", e);
        }
    }
}

