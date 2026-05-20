package com.minduc.happabi.observability.audit;

import java.util.Map;

public record AuditEvent(
        String action,
        String actorId,
        String actorRole,
        String targetResourceType,
        String targetResourceId,
        String status,
        String reason,
        String ipAddress,
        String userAgent,
        String correlationId,
        Map<String, String> metadata
) {
    public AuditEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
