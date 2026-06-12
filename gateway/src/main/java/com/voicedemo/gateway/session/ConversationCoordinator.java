package com.voicedemo.gateway.session;

import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.jobs.AskJobRequest;
import com.voicedemo.gateway.jobs.AskJobResult;
import com.voicedemo.gateway.jobs.AskJobResultType;
import com.voicedemo.gateway.jobs.AskJobService;
import com.voicedemo.gateway.router.RouteDecision;
import com.voicedemo.gateway.router.RouteLabel;
import com.voicedemo.gateway.router.RouterService;
import com.voicedemo.gateway.speech.OutboundMixer;
import com.voicedemo.gateway.speech.TtsClient;
import com.voicedemo.gateway.transcript.TranscriptLine;
import com.voicedemo.gateway.transcript.TranscriptService;
import com.voicedemo.gateway.ws.ControlMessageSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    private final SessionStateMachine stateMachine;

    public ConversationCoordinator(
            TranscriptService transcriptService,
            RouterService routerService,
            EventLogger eventLogger,
            ControlMessageSender controlMessageSender,
            AskJobService askJobService,
            TtsClient ttsClient,
            OutboundMixer outboundMixer,
            SessionStateMachine stateMachine) {
        this.transcriptService = transcriptService;
        this.routerService = routerService;
        this.eventLogger = eventLogger;
        this.controlMessageSender = controlMessageSender;
        this.askJobService = askJobService;
        this.ttsClient = ttsClient;
        this.outboundMixer = outboundMixer;
        this.stateMachine = stateMachine;
    }

    public void onUserUtterance(WebSocketSession socketSession, SessionState state, String text, long endTs) {
        String sessionId = state.sessionId();
        TranscriptLine line = transcriptService.addUserUtterance(sessionId, text, endTs);
        controlMessageSender.userTranscript(socketSession, sessionId, line.utteranceId(), text, endTs);
        eventLogger.log("utterance.end", sessionId, Map.of("utteranceId", line.utteranceId()));

        List<TranscriptLine> window = transcriptService.recent(sessionId, ROUTER_WINDOW);
        RouteDecision decision = routerService.classify(window, text);
        String correlationId = decision.label() == RouteLabel.ASK ? askJobService.newCorrelationId() : null;
        controlMessageSender.routerDecision(socketSession, sessionId, line.utteranceId(), decision, correlationId);
        eventLogger.log("router.decision", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "label", decision.label().name(),
                "correlationId", correlationId == null ? "" : correlationId
        ));

        if (decision.label() == RouteLabel.ASK) {
            state.apply(stateMachine, SessionEvent.ROUTER_ASK);
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

    public void onMoshiText(WebSocketSession socketSession, String sessionId, String text) {
        transcriptService.addMoshiText(sessionId, text);
        controlMessageSender.moshiTranscript(socketSession, sessionId, text);
    }

    public void onSessionClosed(String sessionId) {
        askJobService.cancelSession(sessionId);
        outboundMixer.reset(sessionId);
        transcriptService.remove(sessionId);
    }

    private void onAskJobResult(WebSocketSession socketSession, SessionState state, AskJobResult result) {
        if (result.type() == AskJobResultType.DROPPED) {
            state.apply(stateMachine, SessionEvent.JOB_RESULT_STALE_DROP);
            return;
        }

        state.apply(
                stateMachine,
                result.reintroduced() ? SessionEvent.JOB_RESULT_STALE_REINTRODUCE : SessionEvent.JOB_RESULT_FRESH
        );
        transcriptService.addMoshiText(result.sessionId(), result.text());
        controlMessageSender.moshiTranscript(socketSession, result.sessionId(), result.text());
        controlMessageSender.injectStart(socketSession, result.sessionId(), result.correlationId());
        eventLogger.log("inject.start", result.sessionId(), Map.of(
                "correlationId", result.correlationId(),
                "utteranceId", result.utteranceId()
        ));
        outboundMixer.inject(
                socketSession,
                result.sessionId(),
                result.correlationId(),
                ttsClient.speak(result.text()),
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

    private void injectTextOnlyActReply(WebSocketSession socketSession, String sessionId) {
        String reply = "I can tell that is an action, but actions are coming soon.";
        transcriptService.addMoshiText(sessionId, reply);
        controlMessageSender.moshiTranscript(socketSession, sessionId, reply);
    }
}
