package com.voicedemo.gateway.ws;

import io.github.jaredmdobson.OpusApplication;
import io.github.jaredmdobson.OpusEncoder;
import io.github.jaredmdobson.OpusException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OggOpusEncoder {
    private static final int SAMPLE_RATE = 24_000;
    private static final int OGG_OPUS_GRANULE_RATE = 48_000;
    private static final int CHANNELS = 1;
    private static final int FRAME_SAMPLES = 480;
    private static final int FRAME_BYTES = FRAME_SAMPLES * Short.BYTES;
    private static final int MAX_PACKET_BYTES = 1_275;

    private final OggPageWriter pageWriter = new OggPageWriter();
    private final ByteArrayOutputStream pendingPcm = new ByteArrayOutputStream();
    private final OpusEncoder encoder;
    private boolean sentHeaders;
    private long granulePosition;

    public OggOpusEncoder() {
        try {
            this.encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO);
        } catch (OpusException e) {
            throw new IllegalStateException("failed to create Opus encoder", e);
        }
    }

    public synchronized List<byte[]> encodePcm(byte[] pcmBytes) {
        pendingPcm.writeBytes(pcmBytes);
        List<byte[]> pages = new ArrayList<>();
        if (!sentHeaders) {
            pages.add(pageWriter.page(0x02, 0, opusHead()));
            pages.add(pageWriter.page(0x00, 0, opusTags()));
            sentHeaders = true;
        }

        byte[] data = pendingPcm.toByteArray();
        int offset = 0;
        while (data.length - offset >= FRAME_BYTES) {
            short[] pcm = pcmBytesToShorts(data, offset, FRAME_SAMPLES);
            byte[] packet = encodeFrame(pcm);
            granulePosition += FRAME_SAMPLES * (OGG_OPUS_GRANULE_RATE / SAMPLE_RATE);
            pages.add(pageWriter.page(0x00, granulePosition, packet));
            offset += FRAME_BYTES;
        }

        pendingPcm.reset();
        if (offset < data.length) {
            pendingPcm.writeBytes(Arrays.copyOfRange(data, offset, data.length));
        }
        return pages;
    }

    private byte[] encodeFrame(short[] pcm) {
        byte[] packet = new byte[MAX_PACKET_BYTES];
        try {
            int length = encoder.encode(pcm, 0, FRAME_SAMPLES, packet, 0, packet.length);
            return Arrays.copyOf(packet, length);
        } catch (OpusException e) {
            throw new IllegalStateException("failed to encode PCM frame for Moshi", e);
        }
    }

    private short[] pcmBytesToShorts(byte[] bytes, int offset, int sampleCount) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, sampleCount * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = buffer.getShort();
        }
        return samples;
    }

    private byte[] opusHead() {
        ByteBuffer buffer = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("OpusHead".getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) 1);
        buffer.put((byte) CHANNELS);
        buffer.putShort((short) 0);
        buffer.putInt(SAMPLE_RATE);
        buffer.putShort((short) 0);
        buffer.put((byte) 0);
        return buffer.array();
    }

    private byte[] opusTags() {
        byte[] vendor = "MoshiV2 gateway".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + Integer.BYTES + vendor.length + Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("OpusTags".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(vendor.length);
        buffer.put(vendor);
        buffer.putInt(0);
        return buffer.array();
    }
}
