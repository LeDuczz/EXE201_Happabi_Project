package com.minduc.happabi.entity;

import com.minduc.happabi.enums.KnowledgeStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_knowledge_items", indexes = {
        @Index(name = "idx_ai_knowledge_items_status_updated", columnList = "status, updated_at"),
        @Index(name = "idx_ai_knowledge_items_conversation", columnList = "conversation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "knowledge_item_id")
    private UUID id;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "title", nullable = false, length = 240)
    private String title;

    @Column(name = "source_type", length = 120)
    private String sourceType;

    @Column(name = "source_id", length = 160)
    private String sourceId;

    @Column(name = "language", length = 80)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private KnowledgeStatus status;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "vector_indexed", nullable = false)
    @Builder.Default
    private Boolean vectorIndexed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
