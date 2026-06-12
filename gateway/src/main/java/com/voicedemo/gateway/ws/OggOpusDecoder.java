package com.voicedemo.gateway.ws;

import io.github.jaredmdobson.OpusDecoder;
import io.github.jaredmdobson.OpusException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OggOpusDecoder {
    private static final int OPUS_DECODE_SAMPLE_RATE = 48_000;
    private static final int TARGET_SAMPLE_RATE = 24_000;
    private static final int CHANNELS = 1;
    private static final int MAX_FRAME_SAMPLES = 5_760;

    private final OggPacketReader packetReader = new OggPacketReader();
    private final OpusDecoder decoder;

    public OggOpusDecoder() {
        try {
            this.decoder = new OpusDecoder(OPUS_DECODE_SAMPLE_RATE, CHANNELS);
        } catch (OpusException e) {
            throw new IllegalStateException("failed to create Opus decoder", e);
        }
    }

    public List<byte[]> decode(byte[] oggOpusBytes) {
        List<byte[]> pcmChunks = new ArrayList<>();
        for (byte[] packet : packetReader.append(oggOpusBytes)) {
            if (isOpusHeader(packet) || isOpusTags(packet) || packet.length == 0) {
                continue;
            }
            short[] pcm = new short[MAX_FRAME_SAMPLES * CHANNELS];
            int samples = decodePacket(packet, pcm);
            pcmChunks.add(shortsToPcmBytes(downsampleToTarget(pcm, samples), downsampledCount(samples)));
        }
        return pcmChunks;
    }

    private int decodePacket(byte[] packet, short[] pcm) {
        try {
            return decoder.decode(packet, 0, packet.length, pcm, 0, MAX_FRAME_SAMPLES, false);
        } catch (OpusException e) {
            throw new IllegalStateException("failed to decode Moshi Opus packet", e);
        }
    }

    private short[] downsampleToTarget(short[] pcm, int samples) {
        if (OPUS_DECODE_SAMPLE_RATE == TARGET_SAMPLE_RATE) {
            return Arrays.copyOf(pcm, samples * CHANNELS);
        }
        if (OPUS_DECODE_SAMPLE_RATE != TARGET_SAMPLE_RATE * 2 || CHANNELS != 1) {
            throw new IllegalStateException("unsupported Opus decode rate " + OPUS_DECODE_SAMPLE_RATE);
        }

        short[] downsampled = new short[downsampledCount(samples)];
        for (int input = 0, output = 0; input + 1 < samples; input += 2, output++) {
            downsampled[output] = (short) ((pcm[input] + pcm[input + 1]) / 2);
        }
        return downsampled;
    }

    private int downsampledCount(int samples) {
        if (OPUS_DECODE_SAMPLE_RATE == TARGET_SAMPLE_RATE) {
            return samples * CHANNELS;
        }
        return samples / 2;
    }

    private boolean isOpusHeader(byte[] packet) {
        return startsWith(packet, "OpusHead");
    }

    private boolean isOpusTags(byte[] packet) {
        return startsWith(packet, "OpusTags");
    }

    private boolean startsWith(byte[] packet, String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.US_ASCII);
        return packet.length >= bytes.length && Arrays.equals(Arrays.copyOf(packet, bytes.length), bytes);
    }

    private byte[] shortsToPcmBytes(short[] shorts, int count) {
        ByteBuffer buffer = ByteBuffer.allocate(count * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            buffer.putShort(shorts[i]);
        }
        return buffer.array();
    }
}
