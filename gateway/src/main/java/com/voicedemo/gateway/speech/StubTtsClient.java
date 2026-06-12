package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "voice.tts-mode", havingValue = "stub", matchIfMissing = true)
public class StubTtsClient implements TtsClient {
    private final ModeProperties properties;

    public StubTtsClient(ModeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Flux<byte[]> speak(String text) {
        return Flux.fromIterable(PcmFrames.split(PcmFrames.sine(properties.stubTtsFrameCount(), 660, 0.20)));
    }
}
