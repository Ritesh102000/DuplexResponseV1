package com.voicedemo.gateway.speech;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SpeechTurnScheduler {
    private static final long IDLE_POLL_MS = 50;

    private final OutboundMixer outboundMixer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SpeechTurnScheduler(OutboundMixer outboundMixer) {
        this.outboundMixer = outboundMixer;
    }

    public void speakNow(
            WebSocketSession session,
            String sessionId,
            String correlationId,
            Flux<byte[]> frames,
            Runnable onStart,
            Runnable onComplete) {
        onStart.run();
        outboundMixer.inject(session, sessionId, correlationId, frames, onComplete);
    }

    public void speakWhenIdle(
            WebSocketSession session,
            String sessionId,
            String correlationId,
            Flux<byte[]> frames,
            Runnable onStart,
            Runnable onComplete) {
        if (!outboundMixer.isInjecting(sessionId)) {
            speakNow(session, sessionId, correlationId, frames, onStart, onComplete);
            return;
        }
        scheduler.schedule(
                () -> speakWhenIdle(session, sessionId, correlationId, frames, onStart, onComplete),
                IDLE_POLL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
