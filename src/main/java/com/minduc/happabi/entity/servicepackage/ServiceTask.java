package com.minduc.happabi.entity.servicepackage;

import com.minduc.happabi.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "service_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTask {

    @Id
    @GeneratedValue
    private UUID taskId;

    private String taskName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal basePrice;

    private Integer estimatedMinutes;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;
}