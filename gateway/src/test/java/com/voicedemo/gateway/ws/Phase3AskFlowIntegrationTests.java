package com.voicedemo.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
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
                "voice.event-log-path=target/test-events/phase3-events.jsonl"
        }
)
class Phase3AskFlowIntegrationTests {
    private static final Path EVENT_LOG = Path.of("target/test-events/phase3-events.jsonl");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @BeforeEach
    void clearEventLog() throws IOException {
        Files.deleteIfExists(EVENT_LOG);
    }

    @Test
    void askFlowInjectsTtsAndSuppressesMoshiAudioDuringInjection() throws Exception {
        String sessionId = "phase3-ask-flow";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");

        String decision = handler.awaitRouterDecision();
        assertThat(decision).contains("\"label\":\"ASK\"");
        assertThat(decision).contains("\"correlationId\":\"c-");

        handler.awaitInjectStart();
        handler.awaitBinaryCountAtLeast(1);

        byte[] moshiDuringInjection = pcmFixture((byte) 0x55);
        session.sendMessage(new BinaryMessage(moshiDuringInjection));

        handler.awaitInjectEnd();
        List<String> events = waitForEvents(
                sessionId,
                nodes -> eventNames(nodes).contains("inject.end"),
                Duration.ofSeconds(6)
        ).stream().map(node -> node.path("event").asText()).toList();

        assertEventsInOrder(events, List.of(
                "utterance.end",
                "router.decision",
                "job.dispatched",
                "inject.start",
                "inject.end"
        ));
        assertThat(handler.binaryMessages())
                .noneSatisfy(frame -> assertThat(Arrays.equals(frame, moshiDuringInjection)).isTrue());

        session.close();
    }

    @Test
    void dropsStaleAskResultAfterTooManyNewUserTurns() throws Exception {
        String sessionId = "phase3-stale-drop";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");
        waitForEvents(sessionId, nodes -> eventCount(nodes, "job.dispatched") == 1, Duration.ofSeconds(2));

        sendUser(session, "okay");
        sendUser(session, "that sounds fine");
        sendUser(session, "nice");
        sendUser(session, "I understand");
        sendUser(session, "thanks");

        List<JsonNode> events = waitForEvents(
                sessionId,
                nodes -> hasEvent(nodes, "job.dropped_stale"),
                Duration.ofSeconds(6)
        );
        assertThat(events)
                .anySatisfy(node -> {
                    assertThat(node.path("event").asText()).isEqualTo("job.dropped_stale");
                    assertThat(node.path("turnDelta").asInt()).isGreaterThan(4);
                });
        assertThat(handler.textMessages())
                .noneMatch(message -> message.contains("\"type\":\"inject.start\""));

        session.close();
    }

    @Test
    void supersedesPreviousAskWhenNewAskArrivesForSameSession() throws Exception {
        String sessionId = "phase3-supersede";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");
        waitForEvents(sessionId, nodes -> eventCount(nodes, "job.dispatched") == 1, Duration.ofSeconds(2));

        sendUser(session, "who wrote hamlet");
        handler.awaitInjectEnd();

        List<JsonNode> events = waitForEvents(
                sessionId,
                nodes -> eventCount(nodes, "inject.start") == 1 && hasSupersededDrop(nodes),
                Duration.ofSeconds(6)
        );

        assertThat(events).anySatisfy(node -> {
            assertThat(node.path("event").asText()).isEqualTo("job.dropped_stale");
            assertThat(node.path("reason").asText()).isEqualTo("superseded");
        });

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

    private byte[] pcmFixture(byte value) {
        byte[] pcm = new byte[3840];
        Arrays.fill(pcm, value);
        return pcm;
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
            Thread.sleep(50);
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

    private List<String> eventNames(List<JsonNode> nodes) {
        return nodes.stream().map(node -> node.path("event").asText()).toList();
    }

    private int eventCount(List<JsonNode> nodes, String event) {
        return (int) nodes.stream()
                .filter(node -> event.equals(node.path("event").asText()))
                .count();
    }

    private boolean hasEvent(List<JsonNode> nodes, String event) {
        return eventCount(nodes, event) > 0;
    }

    private boolean hasSupersededDrop(List<JsonNode> nodes) {
        return nodes.stream().anyMatch(node ->
                "job.dropped_stale".equals(node.path("event").asText())
                        && "superseded".equals(node.path("reason").asText()));
    }

    private void assertEventsInOrder(List<String> actual, List<String> expected) {
        int start = -1;
        for (String event : expected) {
            int next = actual.subList(start + 1, actual.size()).indexOf(event);
            assertThat(next)
                    .as("event %s should appear after index %s in %s", event, start, actual)
                    .isGreaterThanOrEqualTo(0);
            start = start + 1 + next;
        }
    }

    private static final class ProbeHandler extends BinaryWebSocketHandler {
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();
        private final CompletableFuture<String> routerDecision = new CompletableFuture<>();
        private final CompletableFuture<String> injectStart = new CompletableFuture<>();
        private final CompletableFuture<String> injectEnd = new CompletableFuture<>();
        private final CompletableFuture<CloseStatus> close = new CompletableFuture<>();
        private final List<String> textMessages = new CopyOnWriteArrayList<>();
        private final List<byte[]> binaryMessages = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            textMessages.add(payload);
            if (payload.contains("\"type\":\"session.start\"")) {
                sessionStart.complete(payload);
            }
            if (payload.contains("\"type\":\"router.decision\"")) {
                routerDecision.complete(payload);
            }
            if (payload.contains("\"type\":\"inject.start\"")) {
                injectStart.complete(payload);
            }
            if (payload.contains("\"type\":\"inject.end\"")) {
                injectEnd.complete(payload);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            binaryMessages.add(bytes(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            close.complete(status);
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

        String awaitInjectEnd() throws Exception {
            return injectEnd.get(6, TimeUnit.SECONDS);
        }

        void awaitBinaryCountAtLeast(int count) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (System.nanoTime() < deadline) {
                if (binaryMessages.size() >= count) {
                    return;
                }
                Thread.sleep(25);
            }
            throw new AssertionError("Timed out waiting for " + count + " binary frames");
        }

        List<String> textMessages() {
            return List.copyOf(textMessages);
        }

        List<byte[]> binaryMessages() {
            return List.copyOf(binaryMessages);
        }

        private byte[] bytes(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }
}
