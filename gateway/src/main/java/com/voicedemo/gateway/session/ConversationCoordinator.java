package com.voicedemo.gateway.session;

import com.voicedemo.gateway.metrics.EventLogger;
import com.voicedemo.gateway.router.RouteDecision;
import com.voicedemo.gateway.router.RouteLabel;
import com.voicedemo.gateway.router.RouterService;
import com.voicedemo.gateway.transcript.TranscriptLine;
import com.voicedemo.gateway.transcript.TranscriptService;
import com.voicedemo.gateway.ws.ControlMessageSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class ConversationCoordinator {
    private static final int ROUTER_WINDOW = 12;

    private final TranscriptService transcriptService;
    private final RouterService routerService;
    private final EventLogger eventLogger;
    private final ControlMessageSender controlMessageSender;

    public ConversationCoordinator(
            TranscriptService transcriptService,
            RouterService routerService,
            EventLogger eventLogger,
            ControlMessageSender controlMessageSender) {
        this.transcriptService = transcriptService;
        this.routerService = routerService;
        this.eventLogger = eventLogger;
        this.controlMessageSender = controlMessageSender;
    }

    public void onUserUtterance(WebSocketSession socketSession, String sessionId, String text, long endTs) {
        TranscriptLine line = transcriptService.addUserUtterance(sessionId, text, endTs);
        controlMessageSender.userTranscript(socketSession, sessionId, line.utteranceId(), text, endTs);
        eventLogger.log("utterance.end", sessionId, Map.of("utteranceId", line.utteranceId()));

        RouteDecision decision = routerService.classify(transcriptService.recent(sessionId, ROUTER_WINDOW), text);
        controlMessageSender.routerDecision(socketSession, sessionId, line.utteranceId(), decision);
        eventLogger.log("router.decision", sessionId, Map.of(
                "utteranceId", line.utteranceId(),
                "label", decision.label().name()
        ));

        if (decision.label() == RouteLabel.ACT) {
            String reply = "I can tell that is an action, but actions are coming soon.";
            transcriptService.addMoshiText(sessionId, reply);
            controlMessageSender.moshiTranscript(socketSession, sessionId, reply);
        }
    }

    public void onMoshiText(WebSocketSession socketSession, String sessionId, String text) {
        transcriptService.addMoshiText(sessionId, text);
        controlMessageSender.moshiTranscript(socketSession, sessionId, text);
    }
}

