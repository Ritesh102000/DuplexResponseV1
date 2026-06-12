package com.voicedemo.gateway.speech;

import com.voicedemo.gateway.config.ModeProperties;
import com.voicedemo.gateway.session.SessionStatus;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BargeInDetector {
    private final int minSpeechMs;
    private final double energyThreshold;
    private final ConcurrentMap<String, SpeechWindow> windows = new ConcurrentHashMap<>();

    public BargeInDetector(ModeProperties properties) {
        this.minSpeechMs = properties.bargeInMinMs();
        this.energyThreshold = properties.bargeInEnergyThreshold();
    }

    public boolean observe(String sessionId, SessionStatus status, byte[] pcm) {
        if (status != SessionStatus.INJECTING) {
            windows.remove(sessionId);
            return false;
        }
        SpeechWindow window = windows.computeIfAbsent(sessionId, ignored -> new SpeechWindow());
        return window.observe(pcm, frameDurationMs(pcm), normalizedRms(pcm), minSpeechMs, energyThreshold);
    }

    public void reset(String sessionId) {
        windows.remove(sessionId);
    }

    private int frameDurationMs(byte[] pcm) {
        int samples = pcm.length / Short.BYTES;
        return Math.max(1, (int) Math.round(samples * 1000.0 / PcmFrames.SAMPLE_RATE));
    }

    private double normalizedRms(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        double sumSquares = 0.0;
        int samples = 0;
        while (buffer.remaining() >= Short.BYTES) {
            double sample = buffer.getShort() / (double) Short.MAX_VALUE;
            sumSquares += sample * sample;
            samples++;
        }
        if (samples == 0) {
            return 0.0;
        }
        return Math.sqrt(sumSquares / samples);
    }

    private static final class SpeechWindow {
        private int speechMs;
        private boolean triggered;

        synchronized boolean observe(
                byte[] pcm,
                int frameMs,
                double rms,
                int minSpeechMs,
                double energyThreshold) {
            if (triggered) {
                return false;
            }
            if (rms >= energyThreshold) {
                speechMs += frameMs;
            } else {
                speechMs = 0;
            }
            if (speechMs >= minSpeechMs) {
                triggered = true;
                return true;
            }
            return false;
        }
    }
}
