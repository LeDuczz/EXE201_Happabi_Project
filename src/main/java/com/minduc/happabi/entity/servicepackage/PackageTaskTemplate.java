package com.minduc.happabi.entity.servicepackage;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "package_task_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageTaskTemplate {

    @Id
    @GeneratedValue
    private UUID packageTaskTemplateId;

    @ManyToOne
    @JoinColumn(name = "day_template_id")
    private PackageDayTemplate dayTemplate;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private ServiceTask task;

    private Integer quantity;
}