package com.minduc.happabi.entity;

import com.minduc.happabi.enums.EkycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_kyc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kyc_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false, unique = true)
    private NurseProfile nurse;

    @Column(name = "cccd_number", length = 20)
    private String cccdNumber;

    @Column(name = "cccd_name", length = 100)
    private String cccdName;

    @Column(name = "cccd_dob")
    private LocalDate cccdDob;

    @Column(name = "cccd_address", columnDefinition = "TEXT")
    private String cccdAddress;

    @Column(name = "cccd_front_s3_key", length = 500)
    private String cccdFrontS3Key;

    @Column(name = "cccd_back_s3_key", length = 500)
    private String cccdBackS3Key;

    @Column(name = "cccd_images_delete_after")
    private OffsetDateTime cccdImagesDeleteAfter;

    @Column(name = "cccd_images_deleted_at")
    private OffsetDateTime cccdImagesDeletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ekyc_status", nullable = false)
    @Builder.Default
    private EkycStatus ekycStatus = EkycStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NurseKyc other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "NurseKyc{" +
                "id=" + id +
                ", cccdNumber='" + cccdNumber + '\'' +
                ", cccdName='" + cccdName + '\'' +
                ", cccdDob=" + cccdDob +
                ", ekycStatus=" + ekycStatus +
                ", reviewedAt=" + reviewedAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
