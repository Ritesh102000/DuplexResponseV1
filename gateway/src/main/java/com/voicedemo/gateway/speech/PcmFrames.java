package com.voicedemo.gateway.speech;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class PcmFrames {
    public static final int SAMPLE_RATE = 24_000;
    public static final int FRAME_SAMPLES = 1_920;
    public static final int FRAME_BYTES = FRAME_SAMPLES * Short.BYTES;

    private PcmFrames() {
    }

    public static List<byte[]> split(byte[] pcm) {
        List<byte[]> frames = new ArrayList<>();
        for (int offset = 0; offset < pcm.length; offset += FRAME_BYTES) {
            int length = Math.min(FRAME_BYTES, pcm.length - offset);
            byte[] frame = new byte[FRAME_BYTES];
            System.arraycopy(pcm, offset, frame, 0, length);
            frames.add(frame);
        }
        return frames;
    }

    public static byte[] sine(int frameCount, double frequency, double amplitude) {
        ByteBuffer buffer = ByteBuffer.allocate(frameCount * FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int samples = frameCount * FRAME_SAMPLES;
        for (int i = 0; i < samples; i++) {
            double fade = Math.min(1.0, Math.min(i / 400.0, (samples - i) / 400.0));
            double value = Math.sin((i / (double) SAMPLE_RATE) * frequency * Math.PI * 2) * amplitude * Math.max(0, fade);
            buffer.putShort((short) Math.round(value * Short.MAX_VALUE));
        }
        return buffer.array();
    }

    public static byte[] stripWavHeaderIfPresent(byte[] audio) {
        if (audio.length < 44 || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F') {
            return audio;
        }
        int offset = 12;
        while (offset + 8 <= audio.length) {
            String chunk = new String(audio, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = ByteBuffer.wrap(audio, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int dataStart = offset + 8;
            if ("data".equals(chunk) && dataStart + size <= audio.length) {
                byte[] pcm = new byte[size];
                System.arraycopy(audio, dataStart, pcm, 0, size);
                return pcm;
            }
            offset = dataStart + size + (size % 2);
        }
        return audio;
    }

    public static byte[] wav(byte[] pcm) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * Short.BYTES);
        header.putShort((short) Short.BYTES);
        header.putShort((short) 16);
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        output.writeBytes(header.array());
        output.writeBytes(pcm);
        return output.toByteArray();
    }
}
