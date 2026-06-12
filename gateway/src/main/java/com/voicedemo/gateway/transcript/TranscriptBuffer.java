package com.voicedemo.gateway.transcript;

import java.util.ArrayList;
import java.util.List;

public class TranscriptBuffer {
    private final int capacity;
    private final List<TranscriptLine> lines = new ArrayList<>();

    public TranscriptBuffer(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(TranscriptLine line) {
        lines.add(line);
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    public synchronized List<TranscriptLine> recent(int limit) {
        int from = Math.max(0, lines.size() - limit);
        return List.copyOf(lines.subList(from, lines.size()));
    }

    public synchronized int size() {
        return lines.size();
    }
}

