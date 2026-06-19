package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingSettlement;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.PlatformRevenue;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.SettlementStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.BookingSettlementRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.PlatformRevenueRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import com.minduc.happabi.service.booking.IBookingSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSettlementServiceImpl implements IBookingSettlementService {

    private final BookingSettlementRepository bookingSettlementRepository;
    private final NurseWalletRepository nurseWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AdminWalletRepository adminWalletRepository;
    private final PlatformRevenueRepository platformRevenueRepository;
    private final IAdminWalletLedgerService adminWalletLedgerService;

    @Override
    @LogExecution
    @TimedAction("SETTLE_COMPLETED_BOOKING")
    @AuditAction(action = "SETTLE_COMPLETED_BOOKING", resourceType = "BOOKING")
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleCompletedWorkSession(WorkSession workSession) {
        if (workSession == null || workSession.getBooking() == null) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED, "Work session or booking is missing.");
        }
        if (!isSettlementAllowed(workSession.getStatus())) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED,
                    "Work session status is not completed: " + workSession.getStatus());
        }

        Booking booking = workSession.getBooking();
        UUID bookingId = booking.getId();
        if (bookingSettlementRepository.findByBookingIdForUpdate(bookingId).isPresent()) {
            log.info("[BookingSettlement] Booking already settled id={}", bookingId);
            return;
        }

        SettlementAmounts amounts = calculateAmounts(booking);
        NurseWallet nurseWallet = lockNurseWallet(workSession.getNurseProfile().getId());
        ensureAdminWalletCanFundPayout(amounts.nurseWalletCreditAmount(), bookingId);

        recordSettlement(workSession, booking, amounts);
        adminWalletLedgerService.recordNursePayout(bookingId, amounts.nurseWalletCreditAmount());
        creditNurseWallet(nurseWallet, bookingId, amounts.nurseWalletCreditAmount());
        recordPlatformRevenue(booking, amounts.platformFeeAmount());
        log.info("[BookingSettlement] Settled booking id={} nurseCredit={} platformFee={}",
                bookingId, amounts.nurseWalletCreditAmount(), amounts.platformFeeAmount());
    }

    private boolean isSettlementAllowed(WorkSessionStatus status) {
        return status == WorkSessionStatus.COMPLETED || status == WorkSessionStatus.AUTO_CONFIRMED;
    }

    private NurseWallet lockNurseWallet(UUID nurseId) {
        return nurseWalletRepository.findByNurseIdForUpdate(nurseId)
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));
    }

    private void ensureAdminWalletCanFundPayout(BigDecimal payoutAmount, UUID bookingId) {
        AdminWallet wallet = adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                .orElseGet(() -> adminWalletRepository.save(AdminWallet.builder()
                        .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                        .balance(BigDecimal.ZERO)
                        .build()));
        if (wallet.getBalance().compareTo(payoutAmount) < 0) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED,
                    "Admin wallet balance is not enough to settle booking " + bookingId);
        }
    }

    private SettlementAmounts calculateAmounts(Booking booking) {
        BigDecimal grossAmount = money(booking.getGrossAmount());
        BigDecimal appCollectedAmount = money(booking.getAppPaymentAmount());
        BigDecimal cashCollectedByNurseAmount = money(booking.getRemainingCashAmount());
        BigDecimal nurseEarningAmount = money(booking.getNurseEarningAmount());
        BigDecimal platformFeeAmount = money(booking.getPlatformFeeAmount());
        BigDecimal nurseWalletCreditAmount = nurseEarningAmount.subtract(cashCollectedByNurseAmount);

        if (nurseWalletCreditAmount.signum() < 0) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED,
                    "Cash amount is greater than nurse earning for booking " + booking.getId());
        }
        if (appCollectedAmount.compareTo(nurseWalletCreditAmount.add(platformFeeAmount)) < 0) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED,
                    "App collected amount is not enough to settle booking " + booking.getId());
        }

        return new SettlementAmounts(
                grossAmount,
                appCollectedAmount,
                cashCollectedByNurseAmount,
                nurseEarningAmount,
                nurseWalletCreditAmount,
                platformFeeAmount
        );
    }

    private BigDecimal money(Long amount) {
        return BigDecimal.valueOf(amount == null ? 0L : amount);
    }

    private void creditNurseWallet(NurseWallet wallet, UUID bookingId, BigDecimal amount) {
        if (amount.signum() == 0) {
            return;
        }
        wallet.setBalance(wallet.getBalance().add(amount));
        nurseWalletRepository.save(wallet);
        walletTransactionRepository.save(WalletTransaction.builder()
                .nurseId(wallet.getNurseId())
                .transactionType(TransactionType.BOOKING_EARNING)
                .amount(amount)
                .walletImpact(amount)
                .depositImpact(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .referenceId(toReferenceId(bookingId))
                .description("Booking earning for booking " + bookingId)
                .build());
    }

    private void recordPlatformRevenue(Booking booking, BigDecimal platformFeeAmount) {
        platformRevenueRepository.save(PlatformRevenue.builder()
                .bookingId(booking.getId().toString())
                .amount(platformFeeAmount)
                .collectedFromNurse(booking.getNurseProfile().getId().toString())
                .build());
    }

    private void recordSettlement(WorkSession workSession, Booking booking, SettlementAmounts amounts) {
        bookingSettlementRepository.save(BookingSettlement.builder()
                .bookingId(booking.getId())
                .workSessionId(workSession.getId())
                .nurseId(workSession.getNurseProfile().getId())
                .paymentOption(booking.getPaymentOption())
                .grossAmount(amounts.grossAmount())
                .appCollectedAmount(amounts.appCollectedAmount())
                .cashCollectedByNurseAmount(amounts.cashCollectedByNurseAmount())
                .nurseEarningAmount(amounts.nurseEarningAmount())
                .nurseWalletCreditAmount(amounts.nurseWalletCreditAmount())
                .platformFeeAmount(amounts.platformFeeAmount())
                .status(SettlementStatus.SUCCESS)
                .build());
    }

    private long toReferenceId(UUID bookingId) {
        return bookingId.getMostSignificantBits() & Long.MAX_VALUE;
    }

    private record SettlementAmounts(
            BigDecimal grossAmount,
            BigDecimal appCollectedAmount,
            BigDecimal cashCollectedByNurseAmount,
            BigDecimal nurseEarningAmount,
            BigDecimal nurseWalletCreditAmount,
            BigDecimal platformFeeAmount
    ) {
    }
}
