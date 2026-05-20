package com.minduc.happabi.entity;

import com.minduc.happabi.observability.audit.AuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_events_actor_id", columnList = "actor_id"),
                @Index(name = "idx_audit_events_action", columnList = "action"),
                @Index(name = "idx_audit_events_target", columnList = "target_resource_type,target_resource_id"),
                @Index(name = "idx_audit_events_correlation_id", columnList = "correlation_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_event_id")
    private UUID id;

    @Column(name = "actor_id", length = 80)
    private String actorId;

    @Column(name = "actor_role", length = 80)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private String action;

    @Column(name = "target_resource_type", length = 100, updatable = false)
    private String targetResourceType;

    @Column(name = "target_resource_id", length = 100, updatable = false)
    private String targetResourceId;

    @Column(name = "status", nullable = false, length = 30, updatable = false)
    private String status;

    @Column(name = "reason", length = 200, updatable = false)
    private String reason;

    @Column(name = "ip_address", length = 80, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private Map<String, String> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AuditEventEntity from(AuditEvent event) {
        return AuditEventEntity.builder()
                .actorId(event.actorId())
                .actorRole(event.actorRole())
                .action(event.action())
                .targetResourceType(event.targetResourceType())
                .targetResourceId(event.targetResourceId())
                .status(event.status())
                .reason(event.reason())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .correlationId(event.correlationId())
                .metadata(event.metadata())
                .build();
    }
}
