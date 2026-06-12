package com.voicedemo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "voice")
public record ModeProperties(
        String moshiMode,
        String moshiWsUrl,
        String sttMode,
        String ttsMode,
        String llmMode,
        int askTimeoutMs,
        int staleTurnLimit,
        String eventLogPath) {
}
