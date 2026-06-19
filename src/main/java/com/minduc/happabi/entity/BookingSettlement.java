package com.minduc.happabi.entity;

import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "booking_settlement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_settlement_booking", columnNames = "booking_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "work_session_id", nullable = false)
    private UUID workSessionId;

    @Column(name = "nurse_id", nullable = false)
    private UUID nurseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_option", nullable = false, length = 40)
    private BookingPaymentOption paymentOption;

    @Column(name = "gross_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "app_collected_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal appCollectedAmount;

    @Column(name = "cash_collected_by_nurse_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal cashCollectedByNurseAmount;

    @Column(name = "nurse_earning_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal nurseEarningAmount;

    @Column(name = "nurse_wallet_credit_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal nurseWalletCreditAmount;

    @Column(name = "platform_fee_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal platformFeeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @CreationTimestamp
    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;
}
