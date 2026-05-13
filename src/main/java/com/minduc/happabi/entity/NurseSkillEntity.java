package com.minduc.happabi.entity;

import com.minduc.happabi.enums.NurseSkill;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "nurse_skills",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"nurse_id", "skill"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "skill_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private NurseProfile nurse;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false)
    private NurseSkill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NurseSkillEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "NurseSkillEntity{" +
                "id=" + id +
                ", skill=" + skill +
                ", verifiedAt=" + verifiedAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
