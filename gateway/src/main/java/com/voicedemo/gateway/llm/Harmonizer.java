package com.voicedemo.gateway.llm;

import com.voicedemo.gateway.transcript.TranscriptLine;

import java.util.List;

public interface Harmonizer {
    String harmonize(String rawAnswer, List<TranscriptLine> recent, boolean reintroduce);
}
