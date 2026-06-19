package com.minduc.happabi.entity;

import com.minduc.happabi.enums.ServiceOfferingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_offerings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_id")
    private UUID id;

    @Column(name = "service_code", nullable = false, unique = true, length = 80)
    private String serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceOfferingType serviceType;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "fit_description", length = 300)
    private String fitDescription;

    @Column(name = "package_content", columnDefinition = "TEXT")
    private String packageContent;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "platform_fee_amount", nullable = false)
    private Long platformFeeAmount;

    @Column(name = "nurse_earning_amount", nullable = false)
    private Long nurseEarningAmount;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
