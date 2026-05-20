package com.minduc.happabi.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MicrometerMetricsRecorder implements MetricsRecorder {

    private final MeterRegistry registry;

    @Override
    public void increment(String name, Map<String, String> tags) {
        registry.counter(name, toTags(tags)).increment();
    }

    @Override
    public void recordDuration(String name, Duration duration, Map<String, String> tags) {
        registry.timer(name, toTags(tags)).record(duration);
    }

    private Tags toTags(Map<String, String> tags) {
        Tags result = Tags.empty();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isBlank()) {
                result = result.and(entry.getKey(), value);
            }
        }
        return result;
    }
}
