package com.minduc.happabi.observability.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.entity.AuditEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditOutboxPayloadFactory {

    private final ObjectMapper objectMapper;

    public String toPayload(AuditEventEntity auditEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("audit_event_id", auditEvent.getId());
        payload.put("eventType", "AUDIT");
        payload.put("source", "outbox");
        payload.put("actor_id", auditEvent.getActorId());
        payload.put("actor_role", auditEvent.getActorRole());
        payload.put("action", auditEvent.getAction());
        payload.put("target_resource_type", auditEvent.getTargetResourceType());
        payload.put("target_resource_id", auditEvent.getTargetResourceId());
        payload.put("status", auditEvent.getStatus());
        payload.put("reason", auditEvent.getReason());
        payload.put("ip_address", auditEvent.getIpAddress());
        payload.put("user_agent", auditEvent.getUserAgent());
        payload.put("correlation_id", auditEvent.getCorrelationId());
        payload.put("metadata", auditEvent.getMetadata());
        payload.put("created_at", auditEvent.getCreatedAt());
        payload.put("@timestamp", auditEvent.getCreatedAt());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit outbox payload", e);
        }
    }
}
