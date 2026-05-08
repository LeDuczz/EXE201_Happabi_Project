package com.minduc.happabi.entity;

import com.minduc.happabi.enums.EkycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_kyc")
@Data
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

    @Column(name = "ocr_confidence", precision = 5, scale = 2)
    private BigDecimal ocrConfidence;

    @Column(name = "face_match_score", precision = 5, scale = 2)
    private BigDecimal faceMatchScore;

    @Column(name = "cccd_front_s3_key", length = 500)
    private String cccdFrontS3Key;

    @Column(name = "cccd_back_s3_key", length = 500)
    private String cccdBackS3Key;

    @Column(name = "selfie_s3_key", length = 500)
    private String selfieS3Key;

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

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
