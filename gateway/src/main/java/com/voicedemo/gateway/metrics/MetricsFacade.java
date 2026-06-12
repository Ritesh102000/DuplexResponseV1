package com.voicedemo.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class MetricsFacade {
    private final MeterRegistry meterRegistry;

    public MetricsFacade(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEvent(String event, Map<String, Object> attributes) {
        Counter.builder("voice.events")
                .description("Voice gateway event count mirrored from the JSONL event log")
                .tag("event", event)
                .tag("label", stringAttribute(attributes, "label", "none"))
                .tag("reason", stringAttribute(attributes, "reason", "none"))
                .register(meterRegistry)
                .increment();

        Long latencyMs = longAttribute(attributes, "latencyMs");
        if (latencyMs != null) {
            Timer.builder("voice.event.latency")
                    .description("Latency values emitted on JSONL events")
                    .tag("event", event)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(Duration.ofMillis(Math.max(0, latencyMs)));
        }
    }

    private String stringAttribute(Map<String, Object> attributes, String key, String fallback) {
        Object value = attributes.get(key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private Long longAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
