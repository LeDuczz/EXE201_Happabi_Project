package com.minduc.happabi.entity;

import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nurse_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NurseProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "license_number", length = 100, unique = true)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialty")
    private NurseSpecialty specialty;

    @Column(name = "experience_years", nullable = false)
    @Builder.Default
    private Integer experienceYears = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "nurse_status", nullable = false)
    @Builder.Default
    private NurseStatus nurseStatus = NurseStatus.PENDING_SUBMIT;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "last_status_changed_at")
    private OffsetDateTime lastStatusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_changed_by")
    private User statusChangedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false)
    @Builder.Default
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.OFFLINE;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "total_completed_jobs", nullable = false)
    @Builder.Default
    private Integer totalCompletedJobs = 0;

    @Column(name = "response_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal responseRate = BigDecimal.ZERO;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "service_area", length = 200)
    private String serviceArea;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "background_checked", nullable = false)
    @Builder.Default
    private Boolean backgroundChecked = false;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
