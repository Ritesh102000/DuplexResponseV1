package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
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
        return Flux.defer(() -> {
                    byte[] audio = webClient.post()
                            .uri("/speak")
                            .bodyValue(Map.of("text", text))
                            .retrieve()
                            .bodyToFlux(DataBuffer.class)
                            .reduce(new ByteArrayOutputStream(), (output, buffer) -> {
                                byte[] chunk = new byte[buffer.readableByteCount()];
                                buffer.read(chunk);
                                DataBufferUtils.release(buffer);
                                output.writeBytes(chunk);
                                return output;
                            })
                            .map(ByteArrayOutputStream::toByteArray)
                            .block(Duration.ofSeconds(15));
                    if (audio == null || audio.length == 0) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(PcmFrames.split(PcmFrames.stripWavHeaderIfPresent(audio)));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
