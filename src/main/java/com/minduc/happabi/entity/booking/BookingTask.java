package com.minduc.happabi.entity.booking;

import com.minduc.happabi.enums.BookingTaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTask {

    @Id
    @GeneratedValue
    private UUID bookingTaskId;

    @ManyToOne
    @JoinColumn(name = "booking_day_id")
    private BookingDay bookingDay;

    private UUID taskId;

    private String taskNameSnapshot;

    private BigDecimal unitPriceSnapshot;

    private Integer quantity;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private BookingTaskStatus taskStatus;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String nurseNotes;
}