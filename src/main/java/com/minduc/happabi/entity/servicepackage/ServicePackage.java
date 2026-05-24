package com.minduc.happabi.entity.servicepackage;

import com.minduc.happabi.enums.PackageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackage {

    @Id
    @GeneratedValue
    private UUID packageId;

    private String packageName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    private PackageStatus status;

    private LocalDateTime createdAt;
}