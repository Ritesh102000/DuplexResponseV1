package com.voicedemo.gateway.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "voice.llm-mode", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {
    @Override
    public String chat(String model, List<Msg> messages, double temp, int maxTokens) {
        return "{\"label\":\"CHAT\",\"confidence\":0.50}";
    }
}

