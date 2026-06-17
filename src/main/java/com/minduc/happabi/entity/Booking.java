package com.minduc.happabi.entity;

import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bookings_slot", columnNames = "slot_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mother_id", nullable = false)
    private User mother;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_profile_id", nullable = false)
    private NurseProfile nurseProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private BookingSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 40)
    @Builder.Default
    private BookingStatus status = BookingStatus.DRAFT;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "hold_expires_at", nullable = false)
    private OffsetDateTime paymentExpiresAt;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "platform_fee_amount", nullable = false)
    private Long platformFeeAmount;

    @Column(name = "nurse_earning_amount", nullable = false)
    private Long nurseEarningAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_option", nullable = false, length = 40)
    @Builder.Default
    private BookingPaymentOption paymentOption = BookingPaymentOption.DEPOSIT_30_PERCENT;

    @Column(name = "deposit_amount", nullable = false)
    private Long depositAmount;

    @Column(name = "remaining_cash_amount", nullable = false)
    private Long remainingCashAmount;

    @Column(name = "app_payment_amount", nullable = false)
    private Long appPaymentAmount;

    @Column(name = "service_address", nullable = false, length = 300)
    private String serviceAddress;

    @Column(name = "mother_note", columnDefinition = "TEXT")
    private String motherNote;

    @Column(name = "hold_key", nullable = false, length = 220, unique = true)
    private String bookingKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
