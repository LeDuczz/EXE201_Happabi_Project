package com.minduc.happabi.observability.audit;

import com.minduc.happabi.entity.AuditEventEntity;
import com.minduc.happabi.entity.OutboxEvent;
import com.minduc.happabi.observability.outbox.OutboxStatus;
import com.minduc.happabi.repository.AuditEventRepository;
import com.minduc.happabi.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Primary
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "observability.audit.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditService implements AuditRecorder {

    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditOutboxPayloadFactory auditOutboxPayloadFactory;

    @Value("${observability.audit.outbox.enabled:true}")
    private boolean auditOutboxEnabled;

    @Value("${observability.audit.outbox.topic:audit.events}")
    private String auditOutboxTopic;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        AuditEventEntity auditEvent = auditEventRepository.saveAndFlush(AuditEventEntity.from(event));
        if (!auditOutboxEnabled) {
            return;
        }

        outboxEventRepository.save(OutboxEvent.builder()
                .topic(auditOutboxTopic)
                .eventType("AUDIT")
                .eventKey(auditEvent.getId().toString())
                .aggregateType(defaultValue(auditEvent.getTargetResourceType(), "AUDIT"))
                .aggregateId(auditEvent.getId().toString())
                .payload(auditOutboxPayloadFactory.toPayload(auditEvent))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
