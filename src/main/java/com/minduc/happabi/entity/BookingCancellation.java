package com.minduc.happabi.entity;

import com.minduc.happabi.enums.BookingCancellationActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "booking_cancellations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_cancellations_booking", columnNames = "booking_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_cancellation_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id", nullable = false)
    private User cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false, length = 30)
    private BookingCancellationActor actor;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "refundable", nullable = false)
    private boolean refundable;

    @Column(name = "refundable_amount", nullable = false)
    private Long refundableAmount;

    @Column(name = "policy_cutoff_at", nullable = false)
    private OffsetDateTime policyCutoffAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
