package com.voicedemo.gateway.transcript;

public record TranscriptLine(
        Speaker speaker,
        String text,
        String utteranceId,
        long ts) {
}

