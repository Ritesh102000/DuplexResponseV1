package com.voicedemo.gateway.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.metrics.MetricsFacade;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RealSttClientTests {
    @Test
    void postsBufferedSpeechToSidecarAfterTrailingSilence(@TempDir Path tempDir) throws Exception {
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/transcribe", exchange -> {
            requestBody.set(exchange.getRequestBody().readAllBytes());
            byte[] response = """
                    [{"text":"what is the capital of australia","endTs":12345}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ModeProperties properties = properties(server, tempDir.resolve("events.jsonl"));
        EventLogger eventLogger = new EventLogger(
                new ObjectMapper(),
                properties,
                new MetricsFacade(new SimpleMeterRegistry())
        );
        RealSttClient client = new RealSttClient(properties, WebClient.builder(), eventLogger);
        try {
            CompletableFuture<String> utterance = new CompletableFuture<>();
            CompletableFuture<Long> endTs = new CompletableFuture<>();
            client.connect("session-a", (text, ts) -> {
                utterance.complete(text);
                endTs.complete(ts);
            });

            byte[] voiced = PcmFrames.sine(1, 440.0, 0.35);
            byte[] silence = new byte[PcmFrames.FRAME_BYTES];
            for (int i = 0; i < 6; i++) {
                client.sendAudio("session-a", voiced);
            }
            for (int i = 0; i < 9; i++) {
                client.sendAudio("session-a", silence);
            }

            assertThat(utterance.get(5, TimeUnit.SECONDS)).isEqualTo("what is the capital of australia");
            assertThat(endTs.get(5, TimeUnit.SECONDS)).isEqualTo(12345L);
            assertThat(requestBody.get()).startsWith("RIFF".getBytes(StandardCharsets.US_ASCII));
        } finally {
            client.destroy();
            server.stop(0);
        }
    }

    private ModeProperties properties(HttpServer server, Path eventLogPath) {
        return new ModeProperties(
                "moshi",
                "stub",
                "ws://localhost:8998/api/chat",
                "real",
                "http://localhost:" + server.getAddress().getPort(),
                0.012,
                400,
                700,
                15_000,
                "stub",
                "http://localhost:8082",
                "stub",
                "https://api.openai.com/v1",
                "",
                "gpt-5.4-mini",
                "gpt-5.4-mini",
                "stub",
                "http://localhost:11434/v1",
                "",
                "qwen3:4b",
                60,
                0.7,
                18,
                4_000,
                20_000,
                2,
                eventLogPath.toString(),
                8,
                250,
                400,
                0.015,
                6,
                "",
                2,
                300,
                20,
                500,
                1500
        );
    }
}
