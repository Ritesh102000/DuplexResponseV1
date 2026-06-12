package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicedemo.gateway.session.ConversationCoordinator;
import com.voicedemo.gateway.session.SessionRegistry;
import com.voicedemo.gateway.session.SessionState;
import com.voicedemo.gateway.speech.AudioInboundPipeline;
import com.voicedemo.gateway.speech.SttClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BrowserSocketHandler extends BinaryWebSocketHandler {
    private final SessionRegistry sessionRegistry;
    private final AudioInboundPipeline audioInboundPipeline;
    private final MoshiClient moshiClient;
    private final SttClient sttClient;
    private final ControlMessageSender controlMessageSender;
    private final ConversationCoordinator conversationCoordinator;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionState> browserSessions = new ConcurrentHashMap<>();

    public BrowserSocketHandler(
            SessionRegistry sessionRegistry,
            AudioInboundPipeline audioInboundPipeline,
            MoshiClient moshiClient,
            SttClient sttClient,
            ControlMessageSender controlMessageSender,
            ConversationCoordinator conversationCoordinator,
            ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.audioInboundPipeline = audioInboundPipeline;
        this.moshiClient = moshiClient;
        this.sttClient = sttClient;
        this.controlMessageSender = controlMessageSender;
        this.conversationCoordinator = conversationCoordinator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        SessionState state = sessionRegistry.create(requestedSessionId(webSocketSession.getUri()));
        browserSessions.put(webSocketSession.getId(), state);
        sttClient.connect(state.sessionId(), (text, endTs) ->
                conversationCoordinator.onUserUtterance(webSocketSession, state.sessionId(), text, endTs));
        moshiClient.connect(state.sessionId(), new MoshiCallbacks() {
            @Override
            public void onOpen() {
                state.markListening();
                controlMessageSender.sessionStart(webSocketSession, state.sessionId());
            }

            @Override
            public void onAudio(byte[] pcm) {
                sendBinary(webSocketSession, pcm);
            }

            @Override
            public void onText(String text) {
                conversationCoordinator.onMoshiText(webSocketSession, state.sessionId(), text);
            }

            @Override
            public void onClose() {
                state.markIdle();
                controlMessageSender.error(webSocketSession, "MOSHI_WS_DROPPED", "Moshi connection closed");
                closeQuietly(webSocketSession);
            }

            @Override
            public void onError(Throwable error) {
                controlMessageSender.error(webSocketSession, "MOSHI_ERROR", error.getMessage());
                closeQuietly(webSocketSession);
            }
        });
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionState state = browserSessions.get(session.getId());
        if (state == null) {
            controlMessageSender.error(session, "SESSION_NOT_FOUND", "No gateway session for browser socket");
            return;
        }
        audioInboundPipeline.onPcmFrame(state.sessionId(), bytes(message.getPayload()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = browserSessions.get(session.getId());
        if (state == null) {
            controlMessageSender.error(session, "SESSION_NOT_FOUND", "No gateway session for browser socket");
            return;
        }
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            if ("transcript.user".equals(json.path("type").asText())) {
                sttClient.submitUtterance(
                        state.sessionId(),
                        json.path("text").asText(),
                        json.path("ts").asLong(System.currentTimeMillis())
                );
            }
        } catch (Exception e) {
            controlMessageSender.error(session, "BAD_CONTROL_MESSAGE", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = browserSessions.remove(session.getId());
        if (state != null) {
            state.markIdle();
            moshiClient.disconnect(state.sessionId());
            sttClient.disconnect(state.sessionId());
            sessionRegistry.remove(state.sessionId());
        }
    }

    private void sendBinary(WebSocketSession session, byte[] pcm) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(pcm));
            }
        } catch (IOException e) {
            controlMessageSender.error(session, "AUDIO_SEND_FAILED", e.getMessage());
            closeQuietly(session);
        }
    }

    private byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private String requestedSessionId(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("sessionId");
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
        }
    }
}
