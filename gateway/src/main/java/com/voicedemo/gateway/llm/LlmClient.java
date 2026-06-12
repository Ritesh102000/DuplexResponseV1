package com.voicedemo.gateway.llm;

import java.util.List;

public interface LlmClient {
    String chat(String model, List<Msg> messages, double temp, int maxTokens);
}

