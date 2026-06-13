package com.voicedemo.gateway.ws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "voice.moshi-mode=stub",
                "voice.stt-mode=stub",
                "voice.tts-mode=stub",
                "voice.llm-mode=stub",
                "voice.ws-bearer-token=test-token"
        }
)
class BearerTokenHandshakeIntegrationTests {
    @LocalServerPort
    int port;

    @Test
    void rejectsVoiceWebSocketWhenBearerTokenIsMissing() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/voice?sessionId=phase6-auth-missing");

        assertThatThrownBy(() -> client.execute(new ProbeHandler(), new WebSocketHttpHeaders(), uri)
                .get(5, TimeUnit.SECONDS))
                .hasMessageContaining("401");
    }

    @Test
    void acceptsVoiceWebSocketWithQueryToken() throws Exception {
        ProbeHandler handler = new ProbeHandler();
        WebSocketSession session = connect(
                "/ws/voice?sessionId=phase6-auth-query&token=test-token",
                new WebSocketHttpHeaders(),
                handler);

        assertThat(handler.awaitSessionStart()).contains("\"sessionId\":\"phase6-auth-query\"");
        session.close();
    }

    @Test
    void acceptsVoiceWebSocketWithAuthorizationBearerHeader() throws Exception {
        ProbeHandler handler = new ProbeHandler();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setBearerAuth("test-token");
        WebSocketSession session = connect("/ws/voice?sessionId=phase6-auth-header", headers, handler);

        assertThat(handler.awaitSessionStart()).contains("\"sessionId\":\"phase6-auth-header\"");
        session.close();
    }

    private WebSocketSession connect(String path, WebSocketHttpHeaders headers, ProbeHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler, headers, URI.create("ws://localhost:" + port + path))
                .get(5, TimeUnit.SECONDS);
    }

    private static final class ProbeHandler extends TextWebSocketHandler {
        private final CompletableFuture<String> sessionStart = new CompletableFuture<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            if (message.getPayload().contains("\"type\":\"session.start\"")) {
                sessionStart.complete(message.getPayload());
            }
        }

        String awaitSessionStart() throws Exception {
            return sessionStart.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
