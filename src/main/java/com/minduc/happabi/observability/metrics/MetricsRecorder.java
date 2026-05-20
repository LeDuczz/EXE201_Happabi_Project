package com.minduc.happabi.observability.metrics;

import java.time.Duration;
import java.util.Map;

public interface MetricsRecorder {

    void increment(String name, Map<String, String> tags);

    void recordDuration(String name, Duration duration, Map<String, String> tags);
}
