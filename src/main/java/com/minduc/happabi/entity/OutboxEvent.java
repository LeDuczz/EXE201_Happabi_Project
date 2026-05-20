package com.minduc.happabi.entity;

import com.minduc.happabi.observability.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_status_next_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_outbox_events_topic", columnList = "topic"),
                @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type,aggregate_id"),
                @Index(name = "idx_outbox_events_event_key", columnList = "event_key")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_event_id")
    private UUID id;

    @Column(name = "topic", nullable = false, length = 120, updatable = false)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 120, updatable = false)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 120, updatable = false)
    private String eventKey;

    @Column(name = "aggregate_type", nullable = false, length = 120, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120, updatable = false)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private OffsetDateTime nextRetryAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now();
        this.lastError = null;
    }

    public void markRetry(String error, int retryDelaySeconds) {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
        this.lastError = error;
        this.nextRetryAt = OffsetDateTime.now().plusSeconds(retryDelaySeconds);
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.lastError = error;
    }
}
