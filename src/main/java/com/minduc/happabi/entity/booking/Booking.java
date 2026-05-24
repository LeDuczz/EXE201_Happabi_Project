package com.minduc.happabi.entity.booking;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.servicepackage.ServicePackage;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.PaymentMethod;
import com.minduc.happabi.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue
    private UUID bookingId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "nurse_id")
    private User nurse;

    @ManyToOne
    @JoinColumn(name = "package_id")
    private ServicePackage servicePackage;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime dailyStartTime;

    private LocalTime dailyEndTime;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus;

    private BigDecimal totalEstimatedAmount;

    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    private LocalDateTime nurseAcceptedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}