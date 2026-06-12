package com.voicedemo.gateway.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "voice.llm-mode", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {
    private static final String STUB_ANSWER = "Canberra is the capital of Australia.";

    @Override
    public String chat(String model, List<Msg> messages, double temp, int maxTokens) {
        String system = messages.isEmpty() ? "" : messages.getFirst().content().toLowerCase();
        if (system.contains("classify")) {
            return "{\"label\":\"CHAT\",\"confidence\":0.50}";
        }
        if (system.contains("rewrite")) {
            String user = messages.size() > 1 ? messages.get(1).content() : "";
            if (user.contains("REINTRODUCE=true")) {
                return "About your earlier question, " + STUB_ANSWER;
            }
            return STUB_ANSWER;
        }
        sleepForDeterministicAskLatency();
        return STUB_ANSWER;
    }

    private void sleepForDeterministicAskLatency() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
