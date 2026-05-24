package com.minduc.happabi.entity.servicepackage;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "package_day_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageDayTemplate {

    @Id
    @GeneratedValue
    private UUID dayTemplateId;

    @ManyToOne
    @JoinColumn(name = "package_id")
    private ServicePackage servicePackage;

    private Integer dayNumber;
}