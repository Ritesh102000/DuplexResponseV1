package com.voicedemo.gateway.ws;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OggOpusCodecTests {

    @Test
    void encodesPcmAsOggPagesAndEmitsHeadersOnlyOnce() {
        OggOpusEncoder encoder = new OggOpusEncoder();

        List<byte[]> first = encoder.encodePcm(pcmSineFrame());
        List<byte[]> second = encoder.encodePcm(pcmSineFrame());

        assertThat(first).hasSize(6);
        assertThat(first).allSatisfy(page ->
                assertThat(page).startsWith(new byte[] {'O', 'g', 'g', 'S'}));
        assertThat(second).hasSize(4);
    }

    @Test
    void roundTripsOneBrowserFrameThroughOpus() {
        OggOpusEncoder encoder = new OggOpusEncoder();
        OggOpusDecoder decoder = new OggOpusDecoder();

        byte[] ogg = join(encoder.encodePcm(pcmSineFrame()));
        List<byte[]> firstHalf = decoder.decode(slice(ogg, 0, 3));
        List<byte[]> secondHalf = decoder.decode(slice(ogg, 3, ogg.length));

        assertThat(firstHalf).isEmpty();
        byte[] decoded = join(secondHalf);
        assertThat(decoded).hasSize(3_840);
        assertThat(maxAbs(decoded)).isGreaterThan(0);
    }

    @Test
    void decodesMoshiSphnOpusFixtureAtBrowserSampleRate() {
        byte[] ogg = Base64.getMimeDecoder().decode("""
                T2dnUwACAAAAAAAAAAAqAAAAAAAAAK7+8/EBE09wdXNIZWFkAQEAD4C7AAAAAABPZ2dTAAAAAAAAAAAAACoAAAABAAAASzwBcQEbT3B1c1RhZ3MLAAAAS3l1dGFpTW9zaGkAAAAAT2dnUwAAgAcAAAAAAAAqAAAAAgAAAPYLVqkBo2pfgi5dbJ6ZrAAABoTvzqBalsSeH4q19JgEPOGUZwrNhtZy9oPIiGKwTFXF7bfd8JxmSGyHfwLRBS2RzegOucDjqn6oTIiRzr2wTrLHmYDpg16tgGJC/v5kK5KNtJrvMS6jP/eslQDvohdxV09enR7JNHwMFq6bYHoWoklivuNEFA7BdP2PH2LB7YuyMB9vBs1mtaoYcKrCAx9VWAAUDxBGqIlPZ2dTAAAADwAAAAAAACoAAAADAAAAD29BPgGIakCbo191nPxLOkYjzCfAoPpgZq6NN4aLxUhKC1/jhBTZmhuowYBd/ipajvHZs7PhpYDK9awoQwMmUUZdjdL7ul9ym6NfdZz8SUo35mnyn+qoEuPgNL10/1Xg56qmwiOqROMvtr/E7/H6XASVZZhYqYEq10yflYdhUbr2OsEpPcR9lTuCiZzdtg==
                """);

        byte[] decoded = join(new OggOpusDecoder().decode(ogg));

        assertThat(decoded).hasSize(3_840);
        assertThat(maxAbs(decoded)).isGreaterThan(1_000);
    }

    private byte[] pcmSineFrame() {
        int sampleRate = 24_000;
        int samples = 1_920;
        ByteBuffer buffer = ByteBuffer.allocate(samples * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double value = Math.sin((i / (double) sampleRate) * 440 * Math.PI * 2) * 0.30;
            buffer.putShort((short) Math.round(value * Short.MAX_VALUE));
        }
        return buffer.array();
    }

    private byte[] join(List<byte[]> chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            output.writeBytes(chunk);
        }
        return output.toByteArray();
    }

    private byte[] slice(byte[] bytes, int from, int to) {
        byte[] slice = new byte[to - from];
        System.arraycopy(bytes, from, slice, 0, slice.length);
        return slice;
    }

    private int maxAbs(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int max = 0;
        while (buffer.remaining() >= Short.BYTES) {
            max = Math.max(max, Math.abs(buffer.getShort()));
        }
        return max;
    }
}
