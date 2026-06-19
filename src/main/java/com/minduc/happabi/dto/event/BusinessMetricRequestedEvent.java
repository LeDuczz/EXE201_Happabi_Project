package com.minduc.happabi.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BusinessMetricRequestedEvent(
        UUID eventId,
        String eventType,
        Instant timestamp,
        BigDecimal amount,
        String status
) {
}
