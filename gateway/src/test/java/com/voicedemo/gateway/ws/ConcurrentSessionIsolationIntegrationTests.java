package com.voicedemo.gateway.ws;

import org.junit.jupiter.api.Test;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "voice.moshi-mode=stub",
                "voice.stt-mode=stub",
                "voice.tts-mode=stub",
                "voice.llm-mode=stub"
        }
)
class ConcurrentSessionIsolationIntegrationTests {
    @LocalServerPort
    int port;

    @Test
    void keepsThreeConcurrentVoiceSessionsIsolated() throws Exception {
        List<SessionProbe> probes = List.of(
                new SessionProbe("phase6-concurrent-a", (byte) 0x11),
                new SessionProbe("phase6-concurrent-b", (byte) 0x22),
                new SessionProbe("phase6-concurrent-c", (byte) 0x33)
        );

        for (SessionProbe probe : probes) {
            probe.open(port);
        }
        for (SessionProbe probe : probes) {
            assertThat(probe.awaitSessionStart()).contains("\"sessionId\":\"" + probe.sessionId + "\"");
        }
        for (SessionProbe probe : probes) {
            probe.sendFrame();
        }
        for (SessionProbe probe : probes) {
            assertThat(probe.awaitFirstBinary()).isEqualTo(probe.frame);
            assertThat(probe.binaryMessages()).allSatisfy(frame -> assertThat(frame).isEqualTo(probe.frame));
        }
        for (SessionProbe probe : probes) {
            probe.close();
        }
    }

    private static final class SessionProbe extends BinaryWebSocketHandler {
        private final String sessionId;
        private final byte[] frame;
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();
        private final CompletableFuture<byte[]> firstBinary = new CompletableFuture<>();
        private final CompletableFuture<CloseStatus> close = new CompletableFuture<>();
        private final List<byte[]> binaryMessages = new CopyOnWriteArrayList<>();
        private WebSocketSession session;

        private SessionProbe(String sessionId, byte fill) {
            this.sessionId = sessionId;
            this.frame = new byte[3840];
            Arrays.fill(this.frame, fill);
        }

        void open(int port) throws Exception {
            StandardWebSocketClient client = new StandardWebSocketClient();
            URI uri = URI.create("ws://localhost:" + port + "/ws/voice?sessionId=" + sessionId);
            this.session = client.execute(this, new WebSocketHttpHeaders(), uri).get(5, TimeUnit.SECONDS);
        }

        void sendFrame() throws Exception {
            session.sendMessage(new BinaryMessage(frame));
        }

        String awaitSessionStart() throws Exception {
            return sessionStart.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        }

        byte[] awaitFirstBinary() throws Exception {
            return firstBinary.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        }

        List<byte[]> binaryMessages() {
            return List.copyOf(binaryMessages);
        }

        void close() throws Exception {
            session.close();
            close.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            if (message.getPayload().contains("\"type\":\"session.start\"")) {
                sessionStart.complete(message.getPayload());
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            byte[] payload = bytes(message.getPayload());
            binaryMessages.add(payload);
            firstBinary.complete(payload);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            close.complete(status);
        }

        private byte[] bytes(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }
}
