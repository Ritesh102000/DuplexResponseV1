package com.voicedemo.gateway.ws;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoshiProtocolTests {

    @Test
    void encodesServerStyleHandshake() {
        assertThat(MoshiProtocol.handshake())
                .containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void decodesAudioFrame() {
        MoshiWireMessage message = MoshiProtocol.decode(new byte[] {1, 10, 20, 30});

        assertThat(message.type()).isEqualTo(MoshiMessageType.AUDIO);
        assertThat(message.payload()).containsExactly(10, 20, 30);
    }

    @Test
    void discardsUnknownFrameType() {
        assertThat(MoshiProtocol.decode(new byte[] {99, 1, 2, 3})).isNull();
    }
}

