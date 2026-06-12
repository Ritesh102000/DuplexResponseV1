package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "voice.tts-mode", havingValue = "real")
public class RealTtsClient implements TtsClient {
    private final WebClient webClient;

    public RealTtsClient(ModeProperties properties, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(properties.ttsUrl()).build();
    }

    @Override
    public Flux<byte[]> speak(String text) {
        byte[] audio = webClient.post()
                .uri("/speak")
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(15));
        if (audio == null || audio.length == 0) {
            return Flux.empty();
        }
        return Flux.fromIterable(PcmFrames.split(PcmFrames.stripWavHeaderIfPresent(audio)));
    }
}
