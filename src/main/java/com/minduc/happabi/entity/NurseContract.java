package com.minduc.happabi.entity;

import com.minduc.happabi.enums.NurseContractStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contract_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private NurseProfile nurse;

    @Column(name = "contract_version", nullable = false, length = 40)
    private String contractVersion;

    @Column(name = "signed_name", length = 150)
    private String signedName;

    @Column(name = "signer_ip", length = 80)
    private String signerIp;

    @Column(name = "signer_user_agent", length = 500)
    private String signerUserAgent;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private NurseContractStatus status = NurseContractStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
