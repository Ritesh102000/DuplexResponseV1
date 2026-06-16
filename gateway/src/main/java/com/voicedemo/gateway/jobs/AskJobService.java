package com.voicedemo.gateway.jobs;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.llm.AnswerService;
import com.voicedemo.gateway.llm.Harmonizer;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.transcript.TranscriptService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class AskJobService {
    private static final String TIMEOUT_TEXT = "Sorry, that took too long, but I can try again.";
    private static final String BACKEND_FALLBACK_TEXT = "Sorry, I could not reach the answer model, but I can try again.";

    private final AnswerService answerService;
    private final Harmonizer harmonizer;
    private final TranscriptService transcriptService;
    private final EventLogger eventLogger;
    private final ModeProperties properties;
    private final ConcurrentMap<String, ActiveAskJob> activeJobs = new ConcurrentHashMap<>();
    private final ExecutorService jobExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    public AskJobService(
            AnswerService answerService,
            Harmonizer harmonizer,
            TranscriptService transcriptService,
            EventLogger eventLogger,
            ModeProperties properties) {
        this.answerService = answerService;
        this.harmonizer = harmonizer;
        this.transcriptService = transcriptService;
        this.eventLogger = eventLogger;
        this.properties = properties;
    }

    public String newCorrelationId() {
        return "c-" + UUID.randomUUID();
    }

    public void dispatch(AskJobRequest request, Consumer<AskJobResult> resultConsumer) {
        ActiveAskJob previous = activeJobs.put(request.sessionId(), new ActiveAskJob(request.correlationId(), null));
        if (previous != null) {
            previous.cancelTimeout();
            eventLogger.log("job.dropped_stale", request.sessionId(), Map.of(
                    "correlationId", previous.correlationId(),
                    "reason", "superseded"
            ));
        }

        eventLogger.log("job.dispatched", request.sessionId(), Map.of(
                "correlationId", request.correlationId(),
                "utteranceId", request.utteranceId()
        ));

        ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                () -> timeout(request, resultConsumer),
                properties.askTimeoutMs(),
                TimeUnit.MILLISECONDS
        );
        activeJobs.put(request.sessionId(), new ActiveAskJob(request.correlationId(), timeout));

        CompletableFuture.runAsync(() -> complete(request, resultConsumer), jobExecutor);
    }

    public void cancelSession(String sessionId) {
        ActiveAskJob active = activeJobs.remove(sessionId);
        if (active != null) {
            active.cancelTimeout();
            eventLogger.log("job.dropped_stale", sessionId, Map.of(
                    "correlationId", active.correlationId(),
                    "reason", "session_closed"
            ));
        }
    }

    @PreDestroy
    public void shutdown() {
        jobExecutor.shutdownNow();
        timeoutScheduler.shutdownNow();
    }

    private void complete(AskJobRequest request, Consumer<AskJobResult> resultConsumer) {
        String rawAnswer;
        boolean fallbackAnswer = false;
        try {
            rawAnswer = answerService.answer(request);
        } catch (Exception e) {
            rawAnswer = BACKEND_FALLBACK_TEXT;
            fallbackAnswer = true;
        }

        ActiveAskJob active = activeJobs.get(request.sessionId());
        if (active == null || !active.matches(request.correlationId())) {
            return;
        }

        active.cancelTimeout();
        long latencyMs = Instant.now().toEpochMilli() - request.dispatchedAt();
        Map<String, Object> completedAttributes = new java.util.LinkedHashMap<>();
        completedAttributes.put("correlationId", request.correlationId());
        completedAttributes.put("utteranceId", request.utteranceId());
        completedAttributes.put("latencyMs", latencyMs);
        if (fallbackAnswer) {
            completedAttributes.put("fallback", true);
            completedAttributes.put("reason", "llm_failure");
        }
        eventLogger.log("job.completed", request.sessionId(), completedAttributes);

        int delta = transcriptService.userTurnIndex(request.sessionId()) - request.userTurnIndexAtDispatch();
        if (delta > properties.staleTurnLimit() + 2) {
            activeJobs.remove(request.sessionId(), active);
            eventLogger.log("job.dropped_stale", request.sessionId(), Map.of(
                    "correlationId", request.correlationId(),
                    "utteranceId", request.utteranceId(),
                    "turnDelta", delta
            ));
            resultConsumer.accept(AskJobResult.dropped(request));
            return;
        }

        boolean reintroduce = delta > properties.staleTurnLimit();
        String text = harmonizeOrFallback(rawAnswer, request, reintroduce);
        activeJobs.remove(request.sessionId(), active);
        resultConsumer.accept(AskJobResult.inject(request, text, reintroduce));
    }

    private String harmonizeOrFallback(String rawAnswer, AskJobRequest request, boolean reintroduce) {
        List<com.voicedemo.gateway.transcript.TranscriptLine> recent = transcriptService.recent(request.sessionId(), 12);
        eventLogger.log("llm.harmonizer.request", request.sessionId(), Map.of(
                "correlationId", request.correlationId(),
                "utteranceId", request.utteranceId(),
                "reintroduce", reintroduce,
                "rawAnswer", rawAnswer,
                "transcriptWindow", formatTranscript(recent)
        ));
        long startedAt = Instant.now().toEpochMilli();
        try {
            String response = harmonizer.harmonize(rawAnswer, recent, reintroduce);
            eventLogger.log("llm.harmonizer.response", request.sessionId(), Map.of(
                    "correlationId", request.correlationId(),
                    "utteranceId", request.utteranceId(),
                    "latencyMs", Instant.now().toEpochMilli() - startedAt,
                    "text", response
            ));
            return response;
        } catch (Exception e) {
            String fallback = reintroduce ? "About your earlier question, " + rawAnswer : rawAnswer;
            eventLogger.log("llm.harmonizer.error", request.sessionId(), Map.of(
                    "correlationId", request.correlationId(),
                    "utteranceId", request.utteranceId(),
                    "latencyMs", Instant.now().toEpochMilli() - startedAt,
                    "error", e.getClass().getSimpleName(),
                    "fallbackText", fallback
            ));
            return fallback;
        }
    }

    private String formatTranscript(List<com.voicedemo.gateway.transcript.TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase() + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }

    private void timeout(AskJobRequest request, Consumer<AskJobResult> resultConsumer) {
        ActiveAskJob active = activeJobs.get(request.sessionId());
        if (active == null || !active.matches(request.correlationId())) {
            return;
        }
        activeJobs.remove(request.sessionId(), active);
        long latencyMs = Instant.now().toEpochMilli() - request.dispatchedAt();
        eventLogger.log("job.completed", request.sessionId(), Map.of(
                "correlationId", request.correlationId(),
                "utteranceId", request.utteranceId(),
                "latencyMs", latencyMs,
                "timeout", true
        ));
        resultConsumer.accept(AskJobResult.inject(request, TIMEOUT_TEXT, false));
    }

    private record ActiveAskJob(String correlationId, ScheduledFuture<?> timeout) {
        boolean matches(String candidate) {
            return correlationId.equals(candidate);
        }

        void cancelTimeout() {
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
    }
}
