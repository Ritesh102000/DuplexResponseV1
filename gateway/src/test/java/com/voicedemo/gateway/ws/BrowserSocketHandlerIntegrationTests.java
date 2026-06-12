package com.voicedemo.gateway.ws;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "voice.moshi-mode=stub"
)
class BrowserSocketHandlerIntegrationTests {
    @LocalServerPort
    int port;

    @Test
    void streamsPcmThroughStubMoshiByteEquivalent() throws Exception {
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect("phase1-audio", handler);
        handler.awaitSessionStart();

        byte[] pcm = pcmFixture();
        session.sendMessage(new BinaryMessage(pcm));

        byte[] echoed = handler.awaitBinary();
        assertThat(echoed).isEqualTo(pcm);

        session.close();
    }

    @Test
    void reconnectsAfterStubMoshiDropAndStartsNewSession() throws Exception {
        ProbeHandler first = new ProbeHandler();
        WebSocketSession firstSession = connect("phase1-reconnect-a", first);
        first.awaitSessionStart();

        firstSession.sendMessage(new BinaryMessage(StubMoshiClient.DROP_FRAME));
        first.awaitClose();

        ProbeHandler second = new ProbeHandler();
        WebSocketSession secondSession = connect("phase1-reconnect-b", second);
        String start = second.awaitSessionStart();

        assertThat(start).contains("\"type\":\"session.start\"");
        assertThat(start).contains("\"sessionId\":\"phase1-reconnect-b\"");

        secondSession.close();
    }

    private WebSocketSession connect(String sessionId, ProbeHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/voice?sessionId=" + sessionId);
        return client.execute(handler, new WebSocketHttpHeaders(), uri)
                .get(5, TimeUnit.SECONDS);
    }

    private byte[] pcmFixture() {
        byte[] pcm = new byte[3840];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i % 127);
        }
        return pcm;
    }

    private static final class ProbeHandler extends BinaryWebSocketHandler {
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();
        private final CompletableFuture<byte[]> firstBinary = new CompletableFuture<>();
        private final CompletableFuture<CloseStatus> close = new CompletableFuture<>();
        private final List<String> textMessages = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            textMessages.add(message.getPayload());
            if (message.getPayload().contains("\"type\":\"session.start\"")) {
                sessionStart.complete(message.getPayload());
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            firstBinary.complete(bytes(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            close.complete(status);
        }

        String awaitSessionStart() throws Exception {
            return sessionStart.get(5, TimeUnit.SECONDS);
        }

        byte[] awaitBinary() throws Exception {
            return firstBinary.get(5, TimeUnit.SECONDS);
        }

        CloseStatus awaitClose() throws Exception {
            return close.get(5, TimeUnit.SECONDS);
        }

        private byte[] bytes(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }
}

