package com.voicedemo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "voice")
public record ModeProperties(
        String moshiMode,
        String moshiWsUrl,
        String sttMode,
        String ttsMode,
        String ttsUrl,
        String llmMode,
        String llmBaseUrl,
        String llmApiKey,
        String llmModelAnswer,
        String llmModelRouter,
        int askTimeoutMs,
        int staleTurnLimit,
        String eventLogPath) {
}
