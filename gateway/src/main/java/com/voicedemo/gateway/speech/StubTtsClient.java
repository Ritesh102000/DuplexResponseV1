package com.voicedemo.gateway.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "voice.tts-mode", havingValue = "stub", matchIfMissing = true)
public class StubTtsClient implements TtsClient {
    @Override
    public Flux<byte[]> speak(String text) {
        return Flux.fromIterable(PcmFrames.split(PcmFrames.sine(6, 660, 0.20)));
    }
}
