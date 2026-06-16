package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.session.ConversationCoordinator;
import com.voicedemo.gateway.metrics.MoshiFirstAudioTracker;
import com.voicedemo.gateway.session.SessionEvent;
import com.voicedemo.gateway.session.SessionRegistry;
import com.voicedemo.gateway.session.SessionState;
import com.voicedemo.gateway.session.SessionStateMachine;
import com.voicedemo.gateway.speech.AudioInboundPipeline;
import com.voicedemo.gateway.speech.BargeInDetector;
import com.voicedemo.gateway.speech.OutboundMixer;
import com.voicedemo.gateway.speech.SuppressionGate;
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
    private final OutboundMixer outboundMixer;
    private final SuppressionGate suppressionGate;
    private final BargeInDetector bargeInDetector;
    private final MoshiFirstAudioTracker moshiFirstAudioTracker;
    private final SessionStateMachine stateMachine;
    private final ModeProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionState> browserSessions = new ConcurrentHashMap<>();

    public BrowserSocketHandler(
            SessionRegistry sessionRegistry,
            AudioInboundPipeline audioInboundPipeline,
            MoshiClient moshiClient,
            SttClient sttClient,
            ControlMessageSender controlMessageSender,
            ConversationCoordinator conversationCoordinator,
            OutboundMixer outboundMixer,
            SuppressionGate suppressionGate,
            BargeInDetector bargeInDetector,
            MoshiFirstAudioTracker moshiFirstAudioTracker,
            SessionStateMachine stateMachine,
            ModeProperties properties,
            ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.audioInboundPipeline = audioInboundPipeline;
        this.moshiClient = moshiClient;
        this.sttClient = sttClient;
        this.controlMessageSender = controlMessageSender;
        this.conversationCoordinator = conversationCoordinator;
        this.outboundMixer = outboundMixer;
        this.suppressionGate = suppressionGate;
        this.bargeInDetector = bargeInDetector;
        this.moshiFirstAudioTracker = moshiFirstAudioTracker;
        this.stateMachine = stateMachine;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        SessionState state = sessionRegistry.create(requestedSessionId(webSocketSession.getUri()));
        browserSessions.put(webSocketSession.getId(), state);
        sttClient.connect(state.sessionId(), (text, endTs) ->
                conversationCoordinator.onUserUtterance(webSocketSession, state, text, endTs));
        if ("qwen".equalsIgnoreCase(properties.runtime())) {
            state.apply(stateMachine, SessionEvent.CLIENT_AND_MOSHI_OPEN);
            controlMessageSender.sessionStart(webSocketSession, state.sessionId());
            return;
        }
        moshiClient.connect(state.sessionId(), new MoshiCallbacks() {
            @Override
            public void onOpen() {
                state.apply(stateMachine, SessionEvent.CLIENT_AND_MOSHI_OPEN);
                controlMessageSender.sessionStart(webSocketSession, state.sessionId());
            }

            @Override
            public void onAudio(byte[] pcm) {
                moshiFirstAudioTracker.onMoshiAudio(state.sessionId());
                outboundMixer.forwardMoshiAudio(
                        webSocketSession,
                        state.sessionId(),
                        suppressionGate.filterMoshiAudio(state.sessionId(), state.status(), pcm)
                );
            }

            @Override
            public void onText(String text) {
                conversationCoordinator.onMoshiText(webSocketSession, state, text);
            }

            @Override
            public void onClose() {
                state.apply(stateMachine, SessionEvent.MOSHI_WS_DROP);
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
        byte[] pcm = bytes(message.getPayload());
        if (bargeInDetector.observe(state.sessionId(), state.status(), pcm)) {
            conversationCoordinator.onBargeIn(session, state);
        }
        audioInboundPipeline.onPcmFrame(state.sessionId(), pcm);
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
            state.apply(stateMachine, SessionEvent.MOSHI_WS_DROP);
            conversationCoordinator.onSessionClosed(state.sessionId());
            moshiClient.disconnect(state.sessionId());
            sttClient.disconnect(state.sessionId());
            sessionRegistry.remove(state.sessionId());
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
