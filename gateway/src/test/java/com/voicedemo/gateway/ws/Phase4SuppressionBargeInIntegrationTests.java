package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "voice.moshi-mode=stub",
                "voice.stt-mode=stub",
                "voice.tts-mode=stub",
                "voice.llm-mode=stub",
                "voice.ask-timeout-ms=5000",
                "voice.stale-turn-limit=2",
                "voice.suppression-token-threshold=8",
                "voice.suppression-fade-ms=250",
                "voice.barge-in-min-ms=400",
                "voice.barge-in-energy-threshold=0.015",
                "voice.stub-tts-frame-count=30",
                "voice.event-log-path=target/test-events/phase4-events.jsonl"
        }
)
class Phase4SuppressionBargeInIntegrationTests {
    private static final Path EVENT_LOG = Path.of("target/test-events/phase4-events.jsonl");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @BeforeEach
    void clearEventLog() throws IOException {
        Files.deleteIfExists(EVENT_LOG);
    }

    @Test
    void fadesLongMoshiAnswerDuringAskInFlightAndLogsSuppression() throws Exception {
        String sessionId = "phase4-long-suppression";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");
        handler.awaitRouterDecision();

        sendMoshiTextFixture(session, "The capital of Australia is Canberra and it is located in the Australian Capital Territory.");
        waitForEvents(sessionId, nodes -> hasEvent(nodes, "suppression.faded"), Duration.ofSeconds(2));

        byte[] loudMoshiAnswer = pcmFixture((short) 24_000);
        for (int i = 0; i < 4; i++) {
            session.sendMessage(new BinaryMessage(loudMoshiAnswer));
        }

        List<byte[]> frames = handler.awaitBinaryCountAtLeast(4);
        assertThat(normalizedRms(frames.get(0))).isGreaterThan(0.25);
        assertThat(normalizedRms(frames.get(3))).isLessThan(0.01);

        session.close();
    }

    @Test
    void passesShortMoshiAcknowledgmentDuringAskInFlight() throws Exception {
        String sessionId = "phase4-ack-passes";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");
        handler.awaitRouterDecision();

        sendMoshiTextFixture(session, "Got it.");
        byte[] acknowledgmentAudio = pcmFixture((short) 12_000);
        session.sendMessage(new BinaryMessage(acknowledgmentAudio));

        byte[] firstAudio = handler.awaitBinary();
        assertThat(firstAudio).isEqualTo(acknowledgmentAudio);
        assertThat(readEvents(sessionId))
                .noneSatisfy(node -> assertThat(node.path("event").asText()).isEqualTo("suppression.faded"));

        session.close();
    }

    @Test
    void bargeInCancelsTtsInjectionWithinFiveHundredMilliseconds() throws Exception {
        String sessionId = "phase4-barge-in";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");
        handler.awaitInjectStart();
        handler.awaitBinaryCountAtLeast(1);

        byte[] userSpeech = pcmFixture((short) 20_000);
        for (int i = 0; i < 5; i++) {
            session.sendMessage(new BinaryMessage(userSpeech));
        }

        List<JsonNode> events = waitForEvents(
                sessionId,
                nodes -> hasEvent(nodes, "barge_in") && hasEvent(nodes, "inject.end"),
                Duration.ofSeconds(2)
        );
        long bargeTs = firstEventTs(events, "barge_in");
        long injectEndTs = lastEventTs(events, "inject.end");
        assertThat(injectEndTs - bargeTs).isBetween(0L, 500L);

        session.close();
    }

    private WebSocketSession connect(String sessionId, ProbeHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/voice?sessionId=" + sessionId);
        return client.execute(handler, new WebSocketHttpHeaders(), uri)
                .get(5, TimeUnit.SECONDS);
    }

    private void sendUser(WebSocketSession session, String text) throws IOException {
        session.sendMessage(new TextMessage("""
                {"type":"transcript.user","text":"%s","ts":1718200000000}
                """.formatted(text)));
    }

    private void sendMoshiTextFixture(WebSocketSession session, String text) throws IOException {
        session.sendMessage(new BinaryMessage(
                (StubMoshiClient.TEXT_FRAME_PREFIX + text).getBytes(StandardCharsets.UTF_8)
        ));
    }

    private byte[] pcmFixture(short sample) {
        byte[] pcm = new byte[3840];
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() >= Short.BYTES) {
            buffer.putShort(sample);
        }
        return pcm;
    }

    private double normalizedRms(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        double sumSquares = 0.0;
        int samples = 0;
        while (buffer.remaining() >= Short.BYTES) {
            double sample = buffer.getShort() / (double) Short.MAX_VALUE;
            sumSquares += sample * sample;
            samples++;
        }
        return Math.sqrt(sumSquares / samples);
    }

    private List<JsonNode> waitForEvents(
            String sessionId,
            Predicate<List<JsonNode>> predicate,
            Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<JsonNode> latest = List.of();
        while (System.nanoTime() < deadline) {
            latest = readEvents(sessionId);
            if (predicate.test(latest)) {
                return latest;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for events. Latest events: " + latest);
    }

    private List<JsonNode> readEvents(String sessionId) throws IOException {
        if (!Files.exists(EVENT_LOG)) {
            return List.of();
        }
        List<JsonNode> events = new ArrayList<>();
        for (String line : Files.readAllLines(EVENT_LOG)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = OBJECT_MAPPER.readTree(line);
            if (sessionId.equals(node.path("sessionId").asText())) {
                events.add(node);
            }
        }
        return events;
    }

    private boolean hasEvent(List<JsonNode> nodes, String event) {
        return nodes.stream().anyMatch(node -> event.equals(node.path("event").asText()));
    }

    private long firstEventTs(List<JsonNode> nodes, String event) {
        return nodes.stream()
                .filter(node -> event.equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow()
                .path("ts")
                .asLong();
    }

    private long lastEventTs(List<JsonNode> nodes, String event) {
        long ts = -1;
        for (JsonNode node : nodes) {
            if (event.equals(node.path("event").asText())) {
                ts = node.path("ts").asLong();
            }
        }
        assertThat(ts).isGreaterThanOrEqualTo(0);
        return ts;
    }

    private static final class ProbeHandler extends BinaryWebSocketHandler {
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();
        private final CompletableFuture<String> routerDecision = new CompletableFuture<>();
        private final CompletableFuture<String> injectStart = new CompletableFuture<>();
        private final CompletableFuture<byte[]> firstBinary = new CompletableFuture<>();
        private final List<byte[]> binaryMessages = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            if (payload.contains("\"type\":\"session.start\"")) {
                sessionStart.complete(payload);
            }
            if (payload.contains("\"type\":\"router.decision\"")) {
                routerDecision.complete(payload);
            }
            if (payload.contains("\"type\":\"inject.start\"")) {
                injectStart.complete(payload);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            byte[] payload = bytes(message.getPayload());
            binaryMessages.add(payload);
            firstBinary.complete(payload);
        }

        String awaitSessionStart() throws Exception {
            return sessionStart.get(5, TimeUnit.SECONDS);
        }

        String awaitRouterDecision() throws Exception {
            return routerDecision.get(5, TimeUnit.SECONDS);
        }

        String awaitInjectStart() throws Exception {
            return injectStart.get(6, TimeUnit.SECONDS);
        }

        byte[] awaitBinary() throws Exception {
            return firstBinary.get(5, TimeUnit.SECONDS);
        }

        List<byte[]> awaitBinaryCountAtLeast(int count) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (System.nanoTime() < deadline) {
                if (binaryMessages.size() >= count) {
                    return List.copyOf(binaryMessages);
                }
                Thread.sleep(25);
            }
            throw new AssertionError("Timed out waiting for " + count + " binary frames. Got "
                    + binaryMessages.size());
        }

        private byte[] bytes(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }
}
