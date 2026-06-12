package com.voicedemo.gateway.router;

import com.voicedemo.gateway.transcript.TranscriptLine;

import java.util.List;

public interface RouterService {
    RouteDecision classify(List<TranscriptLine> window, String utterance);
}

