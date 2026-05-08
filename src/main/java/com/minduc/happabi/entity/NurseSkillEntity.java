package com.minduc.happabi.entity;

import com.minduc.happabi.enums.NurseSkill;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_skills", uniqueConstraints = {@UniqueConstraint(columnNames = {"nurse_id", "skill"})})
@Data
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
}
