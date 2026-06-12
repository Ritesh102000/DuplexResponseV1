package com.voicedemo.gateway.router;

import com.voicedemo.gateway.transcript.TranscriptLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "voice.llm-mode", havingValue = "stub", matchIfMissing = true)
public class HeuristicRouter implements RouterService {
    private static final Set<String> ASK_PREFIXES = Set.of(
            "what", "who", "why", "when", "where", "which", "how",
            "is", "are", "does", "do", "did", "could you compare",
            "can you summarize", "can you explain", "can you give",
            "can you list", "can you make the answer", "explain"
    );

    private static final Set<String> ACT_PREFIXES = Set.of(
            "send", "book", "remind", "add", "schedule", "email", "turn",
            "set", "create", "order", "save", "open", "mute", "start",
            "share", "make a note", "call", "cancel", "move", "download",
            "text", "pause", "switch", "restart", "enable", "copy",
            "raise", "delete", "invite", "mark", "post"
    );

    @Override
    public RouteDecision classify(List<TranscriptLine> window, String utterance) {
        String normalized = normalize(utterance);
        if (normalized.isBlank()) {
            return new RouteDecision(RouteLabel.CHAT, 0.60, "empty utterance defaults to chat");
        }
        if (startsWithAny(normalized, ACT_PREFIXES)) {
            return new RouteDecision(RouteLabel.ACT, 0.88, "imperative action request");
        }
        if (startsWithAny(normalized, ASK_PREFIXES) || containsAskCue(normalized)) {
            return new RouteDecision(RouteLabel.ASK, 0.86, "question or explanation request");
        }
        return new RouteDecision(RouteLabel.CHAT, 0.78, "conversational utterance");
    }

    private boolean containsAskCue(String normalized) {
        return normalized.contains(" explain ")
                || normalized.contains(" summarize ")
                || normalized.contains(" definition ")
                || normalized.contains(" difference between ")
                || normalized.contains(" compare ")
                || normalized.contains(" formula ")
                || normalized.contains(" calculate ")
                || normalized.contains(" command to ")
                || normalized.contains(" how do i ")
                || normalized.contains(" how can i ");
    }

    private boolean startsWithAny(String normalized, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String utterance) {
        String normalized = utterance == null
                ? ""
                : utterance.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.startsWith("please ")) {
            return normalized.substring("please ".length());
        }
        return normalized;
    }
}
