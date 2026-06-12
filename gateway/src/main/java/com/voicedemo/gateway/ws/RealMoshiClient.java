package com.voicedemo.gateway.ws;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "voice.moshi-mode", havingValue = "real")
public class RealMoshiClient implements MoshiClient {
    private final ModeProperties properties;
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public RealMoshiClient(ModeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void connect(String sessionId, MoshiCallbacks callbacks) {
        webSocketClient.execute(new BinaryWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessions.put(sessionId, session);
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
                MoshiWireMessage decoded = MoshiProtocol.decode(bytes(message.getPayload()));
                if (decoded == null) {
                    return;
                }
                switch (decoded.type()) {
                    case HANDSHAKE -> callbacks.onOpen();
                    case AUDIO -> callbacks.onAudio(decoded.payload());
                    case TEXT, COLORED_TEXT -> callbacks.onText(new String(decoded.payload(), StandardCharsets.UTF_8));
                    case ERROR -> callbacks.onError(new IllegalStateException(new String(decoded.payload(), StandardCharsets.UTF_8)));
                    default -> {
                    }
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                sessions.remove(sessionId);
                callbacks.onClose();
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                callbacks.onError(exception);
            }
        }, properties.moshiWsUrl());
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcm) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new BinaryMessage(MoshiProtocol.audio(pcm)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to send audio to Moshi", e);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (IOException ignored) {
        }
    }

    private byte[] bytes(java.nio.ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
