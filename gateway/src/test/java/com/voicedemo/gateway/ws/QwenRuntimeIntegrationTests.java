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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "voice.runtime=qwen",
                "voice.moshi-mode=stub",
                "voice.stt-mode=stub",
                "voice.tts-mode=stub",
                "voice.llm-mode=stub",
                "voice.fast-llm-mode=stub",
                "voice.stub-tts-frame-count=3",
                "voice.ask-timeout-ms=5000",
                "voice.event-log-path=target/test-events/qwen-runtime-events.jsonl"
        }
)
class QwenRuntimeIntegrationTests {
    private static final Path EVENT_LOG = Path.of("target/test-events/qwen-runtime-events.jsonl");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @BeforeEach
    void clearEventLog() throws IOException {
        Files.deleteIfExists(EVENT_LOG);
    }

    @Test
    void chatTurnSpeaksFastReplyWithoutBackendJob() throws Exception {
        String sessionId = "qwen-chat";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "hello there");

        assertThat(handler.awaitRouterDecision()).contains("\"label\":\"CHAT\"");
        assertThat(handler.awaitFastTranscript()).contains("Hey, I'm listening.");
        handler.awaitFastReplyEnd();
        handler.awaitBinaryCountAtLeast(1);

        List<JsonNode> events = waitForEvents(
                sessionId,
                nodes -> hasEvent(nodes, "fast.reply.end"),
                Duration.ofSeconds(3)
        );
        assertThat(eventNames(events)).contains(
                "transcript.user",
                "router.decision",
                "fast.reply.request",
                "fast.reply.response",
                "fast.reply.start",
                "fast.reply.end"
        );
        assertThat(eventNames(events)).doesNotContain("job.dispatched", "backend.inject.start", "inject.start");

        session.close();
    }

    @Test
    void askTurnUsesFastHoldingReplyThenBackendInjection() throws Exception {
        String sessionId = "qwen-ask";
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(sessionId, handler);
        handler.awaitSessionStart();

        sendUser(session, "what is the capital of australia");

        assertThat(handler.awaitRouterDecision()).contains("\"label\":\"ASK\"");
        String fastTranscript = handler.awaitFastTranscript();
        assertThat(fastTranscript).contains("checking");
        assertThat(fastTranscript.toLowerCase()).doesNotContain("canberra");

        handler.awaitFastReplyEnd();
        handler.awaitBackendInjectStart();
        handler.awaitBackendInjectEnd();
        handler.awaitBinaryCountAtLeast(2);

        List<JsonNode> events = waitForEvents(
                sessionId,
                nodes -> hasEvent(nodes, "backend.inject.end") && hasEvent(nodes, "inject.end"),
                Duration.ofSeconds(7)
        );
        assertThat(eventNames(events)).contains(
                "ask.pending.start",
                "fast.reply.request",
                "fast.reply.response",
                "fast.reply.start",
                "fast.reply.end",
                "job.dispatched",
                "job.completed",
                "backend.answer.ready",
                "backend.answer.queued",
                "backend.inject.start",
                "backend.inject.end"
        );
        assertThat(firstEventTs(events, "backend.inject.start"))
                .isGreaterThanOrEqualTo(firstEventTs(events, "fast.reply.end"));
        String fastPrompt = firstEvent(events, "fast.reply.request").toString().toLowerCase();
        assertThat(fastPrompt).contains("state=ask_pending");
        assertThat(fastPrompt).contains("factual geography question");
        assertThat(fastPrompt).doesNotContain("australia");
        assertThat(fastPrompt).doesNotContain("canberra");
        assertThat(fastPrompt).doesNotContain("what is the capital");

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
                {"type":"transcript.user","text":"%s","ts":%d}
                """.formatted(text, System.currentTimeMillis())));
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

    private List<String> eventNames(List<JsonNode> nodes) {
        return nodes.stream().map(node -> node.path("event").asText()).toList();
    }

    private boolean hasEvent(List<JsonNode> nodes, String event) {
        return eventNames(nodes).contains(event);
    }

    private long firstEventTs(List<JsonNode> nodes, String event) {
        return firstEvent(nodes, event).path("ts").asLong();
    }

    private JsonNode firstEvent(List<JsonNode> nodes, String event) {
        return nodes.stream()
                .filter(node -> event.equals(node.path("event").asText()))
                .findFirst()
                .orElseThrow();
    }

    private static final class ProbeHandler extends BinaryWebSocketHandler {
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();
        private final CompletableFuture<String> routerDecision = new CompletableFuture<>();
        private final CompletableFuture<String> fastTranscript = new CompletableFuture<>();
        private final CompletableFuture<String> fastReplyEnd = new CompletableFuture<>();
        private final CompletableFuture<String> backendInjectStart = new CompletableFuture<>();
        private final CompletableFuture<String> backendInjectEnd = new CompletableFuture<>();
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
            if (payload.contains("\"type\":\"transcript.fast\"")) {
                fastTranscript.complete(payload);
            }
            if (payload.contains("\"type\":\"fast.reply.end\"")) {
                fastReplyEnd.complete(payload);
            }
            if (payload.contains("\"type\":\"backend.inject.start\"")) {
                backendInjectStart.complete(payload);
            }
            if (payload.contains("\"type\":\"backend.inject.end\"")) {
                backendInjectEnd.complete(payload);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            binaryMessages.add(bytes(message.getPayload()));
        }

        String awaitSessionStart() throws Exception {
            return sessionStart.get(5, TimeUnit.SECONDS);
        }

        String awaitRouterDecision() throws Exception {
            return routerDecision.get(5, TimeUnit.SECONDS);
        }

        String awaitFastTranscript() throws Exception {
            return fastTranscript.get(5, TimeUnit.SECONDS);
        }

        String awaitFastReplyEnd() throws Exception {
            return fastReplyEnd.get(5, TimeUnit.SECONDS);
        }

        String awaitBackendInjectStart() throws Exception {
            return backendInjectStart.get(7, TimeUnit.SECONDS);
        }

        String awaitBackendInjectEnd() throws Exception {
            return backendInjectEnd.get(7, TimeUnit.SECONDS);
        }

        void awaitBinaryCountAtLeast(int count) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(7).toNanos();
            while (System.nanoTime() < deadline) {
                if (binaryMessages.size() >= count) {
                    return;
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
