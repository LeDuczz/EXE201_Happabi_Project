package com.minduc.happabi.entity.booking;

import com.minduc.happabi.entity.booking.Booking;
import com.minduc.happabi.enums.DailyPaymentStatus;
import com.minduc.happabi.enums.DayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "booking_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDay {

    @Id
    @GeneratedValue
    private UUID bookingDayId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private LocalDate serviceDate;

    private Integer dayNumber;

    private BigDecimal dailyAmount;

    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    private DailyPaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private DayStatus dayStatus;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;
}