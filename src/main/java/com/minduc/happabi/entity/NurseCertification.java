package com.minduc.happabi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_certifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cert_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private NurseProfile nurse;

    @Column(name = "cert_name", nullable = false, length = 200)
    private String certName;

    @Column(name = "issued_by", nullable = false, length = 100)
    private String issuedBy;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "document_s3_key", length = 500)
    private String documentS3Key;

    @Column(name = "rekognition_result", columnDefinition = "JSONB")
    private String rekognitionResult;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;
}
