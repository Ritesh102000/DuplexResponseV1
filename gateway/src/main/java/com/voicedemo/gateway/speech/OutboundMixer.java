package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.ws.ControlMessageSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboundMixer {
    private final Set<String> injectingSessions = ConcurrentHashMap.newKeySet();
    private final ControlMessageSender controlMessageSender;
    private static final Duration FRAME_DURATION = Duration.ofMillis(80);

    public OutboundMixer(ControlMessageSender controlMessageSender) {
        this.controlMessageSender = controlMessageSender;
    }

    public void forwardMoshiAudio(WebSocketSession session, String sessionId, byte[] pcm) {
        if (injectingSessions.contains(sessionId)) {
            return;
        }
        sendBinary(session, pcm);
    }

    public void inject(
            WebSocketSession session,
            String sessionId,
            String correlationId,
            Flux<byte[]> ttsFrames,
            Runnable onComplete) {
        injectingSessions.add(sessionId);
        ttsFrames
                .concatMap(frame -> Mono.fromRunnable(() -> sendBinary(session, frame))
                        .then(Mono.delay(FRAME_DURATION))
                        .then())
                .doOnError(error -> controlMessageSender.error(
                        session,
                        "TTS_STREAM_FAILED",
                        error.getMessage() == null ? "TTS stream failed" : error.getMessage()
                ))
                .doFinally(signal -> {
                    injectingSessions.remove(sessionId);
                    onComplete.run();
                })
                .subscribe();
    }

    public void reset(String sessionId) {
        injectingSessions.remove(sessionId);
    }

    public boolean isInjecting(String sessionId) {
        return injectingSessions.contains(sessionId);
    }

    private void sendBinary(WebSocketSession session, byte[] pcm) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(pcm));
            }
        } catch (IOException | IllegalStateException e) {
            controlMessageSender.error(session, "AUDIO_SEND_FAILED", e.getMessage());
        }
    }
}
