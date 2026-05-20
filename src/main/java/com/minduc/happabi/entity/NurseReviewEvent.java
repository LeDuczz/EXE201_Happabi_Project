package com.minduc.happabi.entity;

import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_review_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private NurseProfile nurse;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private NurseReviewAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private NurseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 50)
    private NurseStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
