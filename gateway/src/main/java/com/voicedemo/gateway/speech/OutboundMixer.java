package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.ws.ControlMessageSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OutboundMixer {
    private final ConcurrentMap<String, ActiveInjection> activeInjections = new ConcurrentHashMap<>();
    private final ControlMessageSender controlMessageSender;
    private static final Duration FRAME_DURATION = Duration.ofMillis(80);

    public OutboundMixer(ControlMessageSender controlMessageSender) {
        this.controlMessageSender = controlMessageSender;
    }

    public void forwardMoshiAudio(WebSocketSession session, String sessionId, byte[] pcm) {
        if (activeInjections.containsKey(sessionId)) {
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
        ActiveInjection active = new ActiveInjection(correlationId);
        ActiveInjection previous = activeInjections.put(sessionId, active);
        if (previous != null) {
            previous.dispose();
        }
        Disposable disposable = ttsFrames
                .concatMap(frame -> Mono.fromRunnable(() -> sendBinary(session, frame))
                        .then(Mono.delay(FRAME_DURATION))
                        .then())
                .doOnError(error -> controlMessageSender.error(
                        session,
                        "TTS_STREAM_FAILED",
                        error.getMessage() == null ? "TTS stream failed" : error.getMessage()
                ))
                .doFinally(signal -> {
                    activeInjections.remove(sessionId, active);
                    onComplete.run();
                })
                .subscribe();
        active.disposable(disposable);
    }

    public Optional<String> cancelInjection(String sessionId) {
        ActiveInjection active = activeInjections.get(sessionId);
        if (active == null) {
            return Optional.empty();
        }
        active.dispose();
        return Optional.of(active.correlationId());
    }

    public Optional<String> activeCorrelationId(String sessionId) {
        ActiveInjection active = activeInjections.get(sessionId);
        return active == null ? Optional.empty() : Optional.of(active.correlationId());
    }

    public void reset(String sessionId) {
        ActiveInjection active = activeInjections.remove(sessionId);
        if (active != null) {
            active.dispose();
        }
    }

    public boolean isInjecting(String sessionId) {
        return activeInjections.containsKey(sessionId);
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

    private static final class ActiveInjection {
        private final String correlationId;
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private ActiveInjection(String correlationId) {
            this.correlationId = correlationId;
        }

        String correlationId() {
            return correlationId;
        }

        void disposable(Disposable candidate) {
            disposable.set(candidate);
            if (cancelled.get()) {
                candidate.dispose();
            }
        }

        void dispose() {
            cancelled.set(true);
            Disposable current = disposable.get();
            if (current != null) {
                current.dispose();
            }
        }
    }
}
