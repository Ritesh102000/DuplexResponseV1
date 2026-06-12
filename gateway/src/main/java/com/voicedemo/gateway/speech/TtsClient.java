package com.voicedemo.gateway.speech;

import reactor.core.publisher.Flux;

public interface TtsClient {
    Flux<byte[]> speak(String text);
}
