package com.voicedemo.gateway.transcript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptBufferTests {

    @Test
    void preservesOrderingWithInterleavedSpeakers() {
        TranscriptBuffer buffer = new TranscriptBuffer(10);

        buffer.add(new TranscriptLine(Speaker.USER, "hello", "u-1", 100));
        buffer.add(new TranscriptLine(Speaker.MOSHI, "hi", null, 110));
        buffer.add(new TranscriptLine(Speaker.USER, "what is websocket", "u-2", 120));

        List<TranscriptLine> lines = buffer.recent(10);
        assertThat(lines).extracting(TranscriptLine::speaker)
                .containsExactly(Speaker.USER, Speaker.MOSHI, Speaker.USER);
        assertThat(lines).extracting(TranscriptLine::text)
                .containsExactly("hello", "hi", "what is websocket");
    }

    @Test
    void keepsOnlyCapacityMostRecentLines() {
        TranscriptBuffer buffer = new TranscriptBuffer(2);

        buffer.add(new TranscriptLine(Speaker.USER, "one", "u-1", 100));
        buffer.add(new TranscriptLine(Speaker.MOSHI, "two", null, 110));
        buffer.add(new TranscriptLine(Speaker.USER, "three", "u-2", 120));

        assertThat(buffer.recent(10)).extracting(TranscriptLine::text)
                .containsExactly("two", "three");
    }
}

