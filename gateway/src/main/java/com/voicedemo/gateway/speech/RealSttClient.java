package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.metrics.EventLogger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "voice.stt-mode", havingValue = "real")
public class RealSttClient implements SttClient, DisposableBean {
    private static final int PRE_ROLL_MS = 320;
    private static final Duration TRANSCRIBE_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient webClient;
    private final EventLogger eventLogger;
    private final double energyThreshold;
    private final int minSpeechMs;
    private final int silenceMs;
    private final int maxUtteranceMs;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, SessionBuffer> sessions = new ConcurrentHashMap<>();

    public RealSttClient(ModeProperties properties, WebClient.Builder builder, EventLogger eventLogger) {
        this.webClient = builder.baseUrl(properties.sttUrl()).build();
        this.eventLogger = eventLogger;
        this.energyThreshold = properties.sttEnergyThreshold();
        this.minSpeechMs = properties.sttMinSpeechMs();
        this.silenceMs = properties.sttSilenceMs();
        this.maxUtteranceMs = properties.sttMaxUtteranceMs();
    }

    @Override
    public void connect(String sessionId, SttCallbacks callbacks) {
        sessions.put(sessionId, new SessionBuffer(callbacks));
    }

    @Override
    public void sendAudio(String sessionId, byte[] pcm) {
        SessionBuffer session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        PendingUtterance pending = session.accept(pcm);
        if (pending != null) {
            transcribe(sessionId, session.callbacks(), pending);
        }
    }

    @Override
    public void submitUtterance(String sessionId, String text, long endTs) {
        SessionBuffer session = sessions.get(sessionId);
        if (session != null && !text.isBlank()) {
            session.callbacks().onUtterance(text.strip(), endTs);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        SessionBuffer session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        PendingUtterance pending = session.flush(System.currentTimeMillis());
        if (pending != null) {
            transcribe(sessionId, session.callbacks(), pending);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private void transcribe(String sessionId, SttCallbacks callbacks, PendingUtterance pending) {
        executor.submit(() -> {
            long startedAt = Instant.now().toEpochMilli();
            int audioMs = durationMs(pending.pcm());
            eventLogger.log("stt.transcribe.start", sessionId, sttPayload(
                    pending.endTs(),
                    audioMs,
                    pending.pcm().length,
                    0,
                    0,
                    ""
            ));
            try {
                UtteranceResponse[] responses = webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/transcribe")
                                .queryParam("sessionId", sessionId)
                                .queryParam("endTs", pending.endTs())
                                .build())
                        .contentType(MediaType.valueOf("audio/wav"))
                        .bodyValue(PcmFrames.wav(pending.pcm()))
                        .retrieve()
                        .bodyToMono(UtteranceResponse[].class)
                        .block(TRANSCRIBE_TIMEOUT);
                if (responses == null) {
                    eventLogger.log("stt.transcribe.response", sessionId, sttPayload(
                            pending.endTs(),
                            audioMs,
                            pending.pcm().length,
                            Instant.now().toEpochMilli() - startedAt,
                            0,
                            ""
                    ));
                    return;
                }
                int responseCount = (int) Arrays.stream(responses)
                        .filter(response -> response != null
                                && response.text() != null
                                && !response.text().isBlank())
                        .count();
                if (responseCount == 0) {
                    eventLogger.log("stt.transcribe.response", sessionId, sttPayload(
                            pending.endTs(),
                            audioMs,
                            pending.pcm().length,
                            Instant.now().toEpochMilli() - startedAt,
                            0,
                            ""
                    ));
                }
                for (UtteranceResponse response : responses) {
                    if (response != null && response.text() != null && !response.text().isBlank()) {
                        eventLogger.log("stt.transcribe.response", sessionId, sttPayload(
                                response.endTs(),
                                audioMs,
                                pending.pcm().length,
                                Instant.now().toEpochMilli() - startedAt,
                                responseCount,
                                ""
                        ));
                        callbacks.onUtterance(response.text().strip(), response.endTs());
                    }
                }
            } catch (RuntimeException e) {
                eventLogger.log("stt.transcribe.error", sessionId, sttPayload(
                        pending.endTs(),
                        audioMs,
                        pending.pcm().length,
                        Instant.now().toEpochMilli() - startedAt,
                        0,
                        e.getClass().getSimpleName()
                ));
                // STT is best-effort. Moshi still owns the live floor if transcription fails.
            }
        });
    }

    private Map<String, Object> sttPayload(
            long endTs,
            int audioMs,
            int audioBytes,
            long latencyMs,
            int responseCount,
            String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("endTs", endTs);
        payload.put("audioMs", audioMs);
        payload.put("audioBytes", audioBytes);
        payload.put("latencyMs", latencyMs);
        payload.put("responseCount", responseCount);
        if (status != null && !status.isBlank()) {
            payload.put("status", status);
        }
        return payload;
    }

    private double rms(byte[] pcm) {
        if (pcm.length < Short.BYTES) {
            return 0.0;
        }
        double sum = 0.0;
        int samples = pcm.length / Short.BYTES;
        for (int i = 0; i + 1 < pcm.length; i += Short.BYTES) {
            int low = pcm[i] & 0xff;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            double normalized = sample / (double) Short.MAX_VALUE;
            sum += normalized * normalized;
        }
        return Math.sqrt(sum / samples);
    }

    private int durationMs(byte[] pcm) {
        int samples = pcm.length / Short.BYTES;
        return Math.max(1, (int) Math.round(samples * 1000.0 / PcmFrames.SAMPLE_RATE));
    }

    private record PendingUtterance(byte[] pcm, long endTs) {
    }

    private record UtteranceResponse(String text, long endTs) {
    }

    private final class SessionBuffer {
        private final SttCallbacks callbacks;
        private final ByteArrayOutputStream utterance = new ByteArrayOutputStream();
        private final Deque<byte[]> preRoll = new ArrayDeque<>();
        private boolean inSpeech;
        private int speechDurationMs;
        private int trailingSilenceMs;

        private SessionBuffer(SttCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        private SttCallbacks callbacks() {
            return callbacks;
        }

        private synchronized PendingUtterance accept(byte[] pcm) {
            byte[] frame = Arrays.copyOf(pcm, pcm.length);
            int frameMs = durationMs(frame);
            boolean voiced = rms(frame) >= energyThreshold;
            long now = System.currentTimeMillis();

            if (!inSpeech && !voiced) {
                rememberPreRoll(frame, frameMs);
                return null;
            }

            if (!inSpeech) {
                startSpeech();
            }

            utterance.writeBytes(frame);
            if (voiced) {
                speechDurationMs += frameMs;
                trailingSilenceMs = 0;
            } else {
                trailingSilenceMs += frameMs;
            }

            if (speechDurationMs >= maxUtteranceMs
                    || (speechDurationMs >= minSpeechMs && trailingSilenceMs >= silenceMs)) {
                return flush(now);
            }
            return null;
        }

        private synchronized PendingUtterance flush(long endTs) {
            if (!inSpeech || speechDurationMs < minSpeechMs || utterance.size() == 0) {
                reset();
                return null;
            }
            byte[] pcm = utterance.toByteArray();
            reset();
            return new PendingUtterance(pcm, endTs);
        }

        private void startSpeech() {
            inSpeech = true;
            utterance.reset();
            for (byte[] frame : preRoll) {
                utterance.writeBytes(frame);
            }
            preRoll.clear();
            speechDurationMs = 0;
            trailingSilenceMs = 0;
        }

        private void rememberPreRoll(byte[] frame, int frameMs) {
            preRoll.addLast(frame);
            int maxFrames = Math.max(1, PRE_ROLL_MS / Math.max(1, frameMs));
            while (preRoll.size() > maxFrames) {
                preRoll.removeFirst();
            }
        }

        private void reset() {
            utterance.reset();
            preRoll.clear();
            inSpeech = false;
            speechDurationMs = 0;
            trailingSilenceMs = 0;
        }
    }
}
