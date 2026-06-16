package com.voicedemo.gateway.session;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.llm.FastConversationMode;
import com.voicedemo.gateway.llm.FastConversationReply;
import com.voicedemo.gateway.llm.FastConversationRequest;
import com.voicedemo.gateway.llm.FastConversationService;
import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.metrics.MoshiFirstAudioTracker;
import com.voicedemo.gateway.jobs.AskJobRequest;
import com.voicedemo.gateway.jobs.AskJobResult;
import com.voicedemo.gateway.jobs.AskJobResultType;
import com.voicedemo.gateway.jobs.AskJobService;
import com.voicedemo.gateway.router.RouteDecision;
import com.voicedemo.gateway.router.RouteLabel;
import com.voicedemo.gateway.router.RouterService;
import com.voicedemo.gateway.speech.OutboundMixer;
import com.voicedemo.gateway.speech.BargeInDetector;
import com.voicedemo.gateway.speech.SpeechTurnScheduler;
import com.voicedemo.gateway.speech.SuppressionGate;
import com.voicedemo.gateway.speech.TtsClient;
import com.voicedemo.gateway.transcript.TranscriptLine;
import com.voicedemo.gateway.transcript.TranscriptService;
import com.voicedemo.gateway.ws.ControlMessageSender;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class ConversationCoordinator {
    private static final int ROUTER_WINDOW = 12;

    private final TranscriptService transcriptService;
    private final RouterService routerService;
    private final EventLogger eventLogger;
    private final ControlMessageSender controlMessageSender;
    private final AskJobService askJobService;
    private final TtsClient ttsClient;
    private final OutboundMixer outboundMixer;
    private final SpeechTurnScheduler speechTurnScheduler;
    private final SuppressionGate suppressionGate;
    private final BargeInDetector bargeInDetector;
    private final MoshiFirstAudioTracker moshiFirstAudioTracker;
    private final SessionStateMachine stateMachine;
    private final FastConversationService fastConversationService;
    private final PendingAnswerRegistry pendingAnswerRegistry;
    private final ModeProperties properties;
    private final ExecutorService fastReplyExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ConversationCoordinator(
            TranscriptService transcriptService,
            RouterService routerService,
            EventLogger eventLogger,
            ControlMessageSender controlMessageSender,
            AskJobService askJobService,
            TtsClient ttsClient,
            OutboundMixer outboundMixer,
            SpeechTurnScheduler speechTurnScheduler,
            SuppressionGate suppressionGate,
            BargeInDetector bargeInDetector,
            MoshiFirstAudioTracker moshiFirstAudioTracker,
            SessionStateMachine stateMachine,
            FastConversationService fastConversationService,
            PendingAnswerRegistry pendingAnswerRegistry,
            ModeProperties properties) {
        this.transcriptService = transcriptService;
        this.routerService = routerService;
        this.eventLogger = eventLogger;
        this.controlMessageSender = controlMessageSender;
        this.askJobService = askJobService;
        this.ttsClient = ttsClient;
        this.outboundMixer = outboundMixer;
        this.speechTurnScheduler = speechTurnScheduler;
        this.suppressionGate = suppressionGate;
        this.bargeInDetector = bargeInDetector;
        this.moshiFirstAudioTracker = moshiFirstAudioTracker;
        this.stateMachine = stateMachine;
        this.fastConversationService = fastConversationService;
        this.pendingAnswerRegistry = pendingAnswerRegistry;
        this.properties = properties;
    }

    public void onUserUtterance(WebSocketSession socketSession, SessionState state, String text, long endTs) {
        String sessionId = state.sessionId();
        if (isQwenRuntime() && isStaleUtterance(endTs)) {
            eventLogger.log("utterance.dropped_stale_stt", sessionId, Map.of(
                    "text", text,
                    "endTs", endTs,
                    "ageMs", Math.max(0, Instant.now().toEpochMilli() - endTs),
                    "thresholdMs", properties.fastStaleUtteranceMs()
            ));
            return;
        }
        if (isQwenRuntime()) {
            state.apply(stateMachine, SessionEvent.USER_UTTERANCE);
        }
        TranscriptLine line = transcriptService.addUserUtterance(sessionId, text, endTs);
        controlMessageSender.userTranscript(socketSession, sessionId, line.utteranceId(), text, endTs);
        eventLogger.log("transcript.user", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "text", text,
                "endTs", endTs
        ));
        eventLogger.log("utterance.end", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "text", text
        ));

        List<TranscriptLine> window = transcriptService.recent(sessionId, ROUTER_WINDOW);
        eventLogger.log("router.input", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "utteranceText", text,
                "transcriptWindow", formatTranscript(window)
        ));
        long routerStartedAt = Instant.now().toEpochMilli();
        RouteDecision decision = routerService.classify(window, text);
        long routerLatencyMs = Instant.now().toEpochMilli() - routerStartedAt;
        String correlationId = decision.label() == RouteLabel.ASK ? askJobService.newCorrelationId() : null;
        controlMessageSender.routerDecision(socketSession, sessionId, line.utteranceId(), decision, correlationId);
        eventLogger.log("router.decision", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "label", decision.label().name(),
                "confidence", decision.confidence(),
                "reason", decision.reason(),
                "correlationId", correlationId == null ? "" : correlationId,
                "latencyMs", routerLatencyMs
        ));

        if (isQwenRuntime()) {
            handleQwenDecision(socketSession, state, line, text, window, decision, correlationId);
            return;
        }

        if (decision.label() == RouteLabel.ASK) {
            state.apply(stateMachine, SessionEvent.ROUTER_ASK);
            suppressionGate.startAsk(sessionId, correlationId);
            moshiFirstAudioTracker.startAsk(sessionId, correlationId, line.utteranceId());
            AskJobRequest request = new AskJobRequest(
                    correlationId,
                    sessionId,
                    line.utteranceId(),
                    text,
                    List.copyOf(window),
                    transcriptService.userTurnIndex(sessionId),
                    Instant.now().toEpochMilli()
            );
            askJobService.dispatch(request, result -> onAskJobResult(socketSession, state, result));
            return;
        }

        if (decision.label() == RouteLabel.ACT) {
            injectTextOnlyActReply(socketSession, sessionId);
        }
    }

    public void onMoshiText(WebSocketSession socketSession, SessionState state, String text) {
        String sessionId = state.sessionId();
        suppressionGate.observeMoshiText(sessionId, state.status(), text);
        TranscriptLine line = transcriptService.addMoshiText(sessionId, text);
        controlMessageSender.moshiTranscript(socketSession, sessionId, text);
        eventLogger.log("transcript.moshi", sessionId, Map.of(
                "text", text,
                "state", state.status().name(),
                "lineTs", line.ts()
        ));
    }

    public void onBargeIn(WebSocketSession socketSession, SessionState state) {
        String sessionId = state.sessionId();
        outboundMixer.activeCorrelationId(sessionId).ifPresent(correlationId -> {
            state.apply(stateMachine, SessionEvent.USER_SPEECH);
            eventLogger.log("barge_in", sessionId, Map.of(
                    "correlationId", correlationId,
                    "abandoned", true
            ));
            if (isQwenRuntime()) {
                eventLogger.log(
                        correlationId.startsWith("f-") ? "fast.reply.canceled" : "backend.inject.canceled",
                        sessionId,
                        Map.of(
                                "correlationId", correlationId,
                                "reason", "barge_in"
                        )
                );
            }
            outboundMixer.cancelInjection(sessionId);
        });
    }

    public void onSessionClosed(String sessionId) {
        askJobService.cancelSession(sessionId);
        outboundMixer.reset(sessionId);
        suppressionGate.reset(sessionId);
        bargeInDetector.reset(sessionId);
        moshiFirstAudioTracker.clear(sessionId);
        pendingAnswerRegistry.clear(sessionId);
        transcriptService.remove(sessionId);
    }

    @PreDestroy
    public void shutdown() {
        fastReplyExecutor.shutdownNow();
    }

    private void onAskJobResult(WebSocketSession socketSession, SessionState state, AskJobResult result) {
        if (isQwenRuntime()) {
            onQwenAskJobResult(socketSession, state, result);
            return;
        }
        if (result.type() == AskJobResultType.DROPPED) {
            state.apply(stateMachine, SessionEvent.JOB_RESULT_STALE_DROP);
            suppressionGate.endAsk(result.sessionId());
            moshiFirstAudioTracker.clear(result.sessionId());
            return;
        }

        state.apply(
                stateMachine,
                result.reintroduced() ? SessionEvent.JOB_RESULT_STALE_REINTRODUCE : SessionEvent.JOB_RESULT_FRESH
        );
        suppressionGate.endAsk(result.sessionId());
        moshiFirstAudioTracker.clear(result.sessionId());
        transcriptService.addMoshiText(result.sessionId(), result.text());
        controlMessageSender.moshiTranscript(socketSession, result.sessionId(), result.text());
        controlMessageSender.injectStart(socketSession, result.sessionId(), result.correlationId());
        eventLogger.log("handoff.inject.text", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId(),
                "text", result.text(),
                "reintroduced", result.reintroduced()
        ));
        eventLogger.log("inject.start", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId()
        ));
        outboundMixer.inject(
                socketSession,
                result.sessionId(),
                result.correlationId(),
                timedTts(
                        result.sessionId(),
                        result.utteranceId(),
                        result.correlationId(),
                        "BACKEND_INJECT",
                        "backend",
                        result.text()
                ),
                () -> {
                    state.apply(stateMachine, SessionEvent.TTS_STREAM_ENDS);
                    controlMessageSender.injectEnd(socketSession, result.sessionId(), result.correlationId());
                    eventLogger.log("inject.end", result.sessionId(), Map.of(
                            "correlationId", result.correlationId(),
                            "utteranceId", result.utteranceId()
                    ));
                }
        );
    }

    private void handleQwenDecision(
            WebSocketSession socketSession,
            SessionState state,
            TranscriptLine line,
            String text,
            List<TranscriptLine> window,
            RouteDecision decision,
            String correlationId) {
        String sessionId = state.sessionId();
        Optional<PendingAnswerRegistry.PendingAnswer> currentPending = pendingAnswerRegistry.current(sessionId);

        if (decision.label() == RouteLabel.ASK) {
            state.apply(stateMachine, SessionEvent.ROUTER_ASK_PENDING);
            currentPending.ifPresent(previous -> eventLogger.log("ask.pending.superseded", sessionId, Map.of(
                    "correlationId", previous.correlationId(),
                    "newCorrelationId", correlationId
            )));
            PendingAnswerRegistry.PendingAnswer pending = pendingAnswerRegistry.start(
                    sessionId,
                    correlationId,
                    line.utteranceId(),
                    text,
                    transcriptService.userTurnIndex(sessionId)
            );
            eventLogger.log("ask.pending.start", sessionId, Map.of(
                    "correlationId", correlationId,
                    "utteranceId", line.utteranceId(),
                    "question", text
            ));
            AskJobRequest request = new AskJobRequest(
                    correlationId,
                    sessionId,
                    line.utteranceId(),
                    text,
                    List.copyOf(window),
                    transcriptService.userTurnIndex(sessionId),
                    Instant.now().toEpochMilli()
            );
            askJobService.dispatch(request, result -> onAskJobResult(socketSession, state, result));
            startFastReply(socketSession, state, FastConversationMode.ASK_PENDING, line, text, pending.question(), correlationId);
            return;
        }

        if (currentPending.isPresent()) {
            PendingAnswerRegistry.PendingAnswer pending = currentPending.get();
            startFastReply(
                    socketSession,
                    state,
                    FastConversationMode.ASK_PENDING,
                    line,
                    text,
                    pending.question(),
                    pending.correlationId()
            );
            return;
        }

        if (decision.label() == RouteLabel.ACT) {
            state.apply(stateMachine, SessionEvent.ROUTER_CHAT);
            startCannedFastReply(socketSession, state, line.utteranceId(), "Actions are coming soon.");
            return;
        }

        state.apply(stateMachine, SessionEvent.ROUTER_CHAT);
        startFastReply(socketSession, state, FastConversationMode.CHAT, line, text, "", "f-" + line.utteranceId());
    }

    private void startFastReply(
            WebSocketSession socketSession,
            SessionState state,
            FastConversationMode mode,
            TranscriptLine line,
            String latestUserText,
            String pendingQuestion,
            String correlationId) {
        String sessionId = state.sessionId();
        if (mode == FastConversationMode.ASK_PENDING) {
            state.apply(stateMachine, SessionEvent.FAST_REPLY_REQUESTED);
        }
        List<TranscriptLine> window = transcriptService.recent(sessionId, ROUTER_WINDOW);
        FastConversationRequest request = new FastConversationRequest(
                mode,
                sessionId,
                line.utteranceId(),
                correlationId,
                latestUserText,
                pendingQuestion,
                List.copyOf(window)
        );
        fastReplyExecutor.submit(() -> {
            FastConversationReply reply = fastConversationService.reply(request);
            if (request.mode() == FastConversationMode.ASK_PENDING
                    && !shouldStillSpeakPendingFastReply(request.sessionId(), request.correlationId())) {
                eventLogger.log("fast.reply.canceled", request.sessionId(), Map.of(
                        "utteranceId", request.utteranceId(),
                        "correlationId", request.correlationId(),
                        "reason", "backend_ready_or_not_current"
                ));
                return;
            }
            state.apply(stateMachine, SessionEvent.FAST_REPLY_READY);
            speakFastReply(socketSession, state, request, reply.text());
        });
    }

    private void startCannedFastReply(
            WebSocketSession socketSession,
            SessionState state,
            String utteranceId,
            String text) {
        String sessionId = state.sessionId();
        String correlationId = "f-" + utteranceId;
        FastConversationRequest request = new FastConversationRequest(
                FastConversationMode.CHAT,
                sessionId,
                utteranceId,
                correlationId,
                text,
                "",
                List.of()
        );
        state.apply(stateMachine, SessionEvent.FAST_REPLY_READY);
        speakFastReply(socketSession, state, request, text);
    }

    private void speakFastReply(
            WebSocketSession socketSession,
            SessionState state,
            FastConversationRequest request,
            String text) {
        transcriptService.addFastText(request.sessionId(), text);
        controlMessageSender.fastTranscript(socketSession, request.sessionId(), text);
        speechTurnScheduler.speakNow(
                socketSession,
                request.sessionId(),
                request.correlationId(),
                timedTts(request, "fast", text),
                () -> {
                    controlMessageSender.fastReplyStart(socketSession, request.sessionId(), request.correlationId());
                    eventLogger.log("fast.reply.start", request.sessionId(), Map.of(
                            "utteranceId", request.utteranceId(),
                            "correlationId", request.correlationId(),
                            "promptMode", request.mode().name(),
                            "text", text
                    ));
                },
                () -> {
                    state.apply(stateMachine, SessionEvent.TTS_STREAM_ENDS);
                    controlMessageSender.fastReplyEnd(socketSession, request.sessionId(), request.correlationId());
                    eventLogger.log("fast.reply.end", request.sessionId(), Map.of(
                            "utteranceId", request.utteranceId(),
                            "correlationId", request.correlationId(),
                            "promptMode", request.mode().name()
                    ));
                }
        );
    }

    private void onQwenAskJobResult(WebSocketSession socketSession, SessionState state, AskJobResult result) {
        if (result.type() == AskJobResultType.DROPPED) {
            pendingAnswerRegistry.clearIfCurrent(result.sessionId(), result.correlationId());
            eventLogger.log("backend.inject.canceled", result.sessionId(), Map.of(
                    "correlationId", result.correlationId(),
                    "utteranceId", result.utteranceId(),
                    "reason", "dropped_stale"
            ));
            state.apply(stateMachine, SessionEvent.JOB_RESULT_STALE_DROP);
            return;
        }
        if (!pendingAnswerRegistry.isCurrent(result.sessionId(), result.correlationId())) {
            eventLogger.log("backend.inject.canceled", result.sessionId(), Map.of(
                    "correlationId", result.correlationId(),
                    "utteranceId", result.utteranceId(),
                    "reason", "not_current"
            ));
            return;
        }

        pendingAnswerRegistry.markReady(result.sessionId(), result.correlationId());
        state.apply(stateMachine, SessionEvent.BACKEND_ANSWER_READY);
        eventLogger.log("backend.answer.ready", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId(),
                "reintroduced", result.reintroduced()
        ));
        eventLogger.log("backend.answer.queued", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId()
        ));

        eventLogger.log("handoff.inject.text", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId(),
                "text", result.text(),
                "reintroduced", result.reintroduced()
        ));
        speechTurnScheduler.speakWhenIdle(
                socketSession,
                result.sessionId(),
                result.correlationId(),
                timedTts(
                        result.sessionId(),
                        result.utteranceId(),
                        result.correlationId(),
                        "BACKEND_INJECT",
                        "backend",
                        result.text()
                ),
                () -> {
                    state.apply(stateMachine, SessionEvent.BACKEND_INJECT_STARTED);
                    transcriptService.addBackendText(result.sessionId(), result.text());
                    controlMessageSender.backendTranscript(socketSession, result.sessionId(), result.text());
                    controlMessageSender.backendInjectStart(socketSession, result.sessionId(), result.correlationId());
                    controlMessageSender.injectStart(socketSession, result.sessionId(), result.correlationId());
                    eventLogger.log("backend.inject.start", result.sessionId(), Map.of(
                            "correlationId", result.correlationId(),
                            "utteranceId", result.utteranceId()
                    ));
                    eventLogger.log("inject.start", result.sessionId(), Map.of(
                            "correlationId", result.correlationId(),
                            "utteranceId", result.utteranceId()
                    ));
                },
                () -> {
                    state.apply(stateMachine, SessionEvent.TTS_STREAM_ENDS);
                    controlMessageSender.backendInjectEnd(socketSession, result.sessionId(), result.correlationId());
                    controlMessageSender.injectEnd(socketSession, result.sessionId(), result.correlationId());
                    eventLogger.log("backend.inject.end", result.sessionId(), Map.of(
                            "correlationId", result.correlationId(),
                            "utteranceId", result.utteranceId()
                    ));
                    eventLogger.log("inject.end", result.sessionId(), Map.of(
                            "correlationId", result.correlationId(),
                            "utteranceId", result.utteranceId()
                    ));
                    pendingAnswerRegistry.clearIfCurrent(result.sessionId(), result.correlationId());
                }
        );
    }

    private Flux<byte[]> timedTts(FastConversationRequest request, String phase, String text) {
        return timedTts(
                request.sessionId(),
                request.utteranceId(),
                request.correlationId(),
                request.mode().name(),
                phase,
                text
        );
    }

    private Flux<byte[]> timedTts(
            String sessionId,
            String utteranceId,
            String correlationId,
            String promptMode,
            String phase,
            String text) {
        return Flux.defer(() -> {
            long startedAt = Instant.now().toEpochMilli();
            AtomicBoolean firstFrameSeen = new AtomicBoolean(false);
            AtomicInteger frameCount = new AtomicInteger();
            eventLogger.log("tts.request", sessionId, ttsPayload(
                    utteranceId,
                    correlationId,
                    promptMode,
                    phase,
                    0,
                    0,
                    text,
                    ""
            ));
            return ttsClient.speak(text)
                    .doOnNext(frame -> {
                        int frames = frameCount.incrementAndGet();
                        if (firstFrameSeen.compareAndSet(false, true)) {
                            eventLogger.log("tts.first_frame", sessionId, ttsPayload(
                                    utteranceId,
                                    correlationId,
                                    promptMode,
                                    phase,
                                    Instant.now().toEpochMilli() - startedAt,
                                    frames,
                                    text,
                                    ""
                            ));
                        }
                    })
                    .doOnError(error -> eventLogger.log("tts.error", sessionId, ttsPayload(
                            utteranceId,
                            correlationId,
                            promptMode,
                            phase,
                            Instant.now().toEpochMilli() - startedAt,
                            frameCount.get(),
                            text,
                            error.getClass().getSimpleName()
                    )))
                    .doFinally(signal -> eventLogger.log("tts.end", sessionId, ttsPayload(
                            utteranceId,
                            correlationId,
                            promptMode,
                            phase,
                            Instant.now().toEpochMilli() - startedAt,
                            frameCount.get(),
                            text,
                            signal.name()
                    )));
        });
    }

    private Map<String, Object> ttsPayload(
            String utteranceId,
            String correlationId,
            String promptMode,
            String phase,
            long latencyMs,
            int frameCount,
            String text,
            String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("utteranceId", utteranceId == null ? "" : utteranceId);
        payload.put("correlationId", correlationId == null ? "" : correlationId);
        payload.put("promptMode", promptMode == null ? "" : promptMode);
        payload.put("phase", phase);
        payload.put("latencyMs", latencyMs);
        payload.put("frameCount", frameCount);
        payload.put("textLength", text == null ? 0 : text.length());
        if (status != null && !status.isBlank()) {
            payload.put("status", status);
        }
        return payload;
    }

    private void injectTextOnlyActReply(WebSocketSession socketSession, String sessionId) {
        String reply = "I can tell that is an action, but actions are coming soon.";
        transcriptService.addMoshiText(sessionId, reply);
        controlMessageSender.moshiTranscript(socketSession, sessionId, reply);
    }

    private boolean isQwenRuntime() {
        return "qwen".equalsIgnoreCase(properties.runtime());
    }

    private boolean shouldStillSpeakPendingFastReply(String sessionId, String correlationId) {
        return pendingAnswerRegistry.current(sessionId)
                .filter(pending -> pending.correlationId().equals(correlationId))
                .map(pending -> pending.status() == PendingAnswerRegistry.PendingAnswerStatus.IN_FLIGHT)
                .orElse(false);
    }

    private boolean isStaleUtterance(long endTs) {
        if (endTs <= 0) {
            return false;
        }
        return Instant.now().toEpochMilli() - endTs > properties.fastStaleUtteranceMs();
    }

    private String formatTranscript(List<TranscriptLine> lines) {
        return lines.stream()
                .map(line -> line.speaker().name().toLowerCase() + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }
}
