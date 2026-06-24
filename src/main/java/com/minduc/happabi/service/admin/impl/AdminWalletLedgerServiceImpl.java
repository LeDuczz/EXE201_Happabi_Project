package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.dto.response.admin.AdminWalletResponse;
import com.minduc.happabi.dto.response.admin.AdminWalletTransactionResponse;
import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.AdminWalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWalletLedgerServiceImpl implements IAdminWalletLedgerService {

    private final AdminWalletRepository adminWalletRepository;
    private final AdminWalletTransactionRepository adminWalletTransactionRepository;

    @Override
    @LogExecution
    @TimedAction("ADMIN_WALLET_RECORD_BOOKING_PAYMENT_RECEIVED")
    @AuditAction(action = "ADMIN_WALLET_RECORD_BOOKING_PAYMENT_RECEIVED", resourceType = "ADMIN_WALLET")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBookingPaymentReceived(UUID bookingId, BigDecimal amount) {
        recordTransaction(
                bookingId,
                AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED,
                positive(amount),
                positive(amount),
                "Booking payment received for booking " + bookingId
        );
    }

    @Override
    @LogExecution
    @TimedAction("ADMIN_WALLET_RECORD_PAYMENT_GATEWAY_FEE")
    @AuditAction(action = "ADMIN_WALLET_RECORD_PAYMENT_GATEWAY_FEE", resourceType = "ADMIN_WALLET")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentGatewayFee(UUID bookingId, BigDecimal amount) {
        BigDecimal feeAmount = positive(amount);
        recordTransaction(
                bookingId,
                AdminWalletTransactionType.PAYMENT_GATEWAY_FEE,
                feeAmount,
                feeAmount.negate(),
                "Payment gateway fee for booking " + bookingId
        );
    }

    @Override
    @LogExecution
    @TimedAction("ADMIN_WALLET_RECORD_NURSE_PAYOUT")
    @AuditAction(action = "ADMIN_WALLET_RECORD_NURSE_PAYOUT", resourceType = "ADMIN_WALLET")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordNursePayout(UUID bookingId, BigDecimal amount) {
        BigDecimal payoutAmount = positive(amount);
        if (payoutAmount.signum() == 0) {
            return;
        }
        recordTransaction(
                bookingId,
                AdminWalletTransactionType.NURSE_PAYOUT,
                payoutAmount,
                payoutAmount.negate(),
                "Nurse payout for booking " + bookingId
        );
    }

    @Override
    @LogExecution
    @TimedAction("ADMIN_WALLET_RECORD_BOOKING_REFUND")
    @AuditAction(action = "ADMIN_WALLET_RECORD_BOOKING_REFUND", resourceType = "ADMIN_WALLET")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBookingRefund(UUID bookingId, BigDecimal amount) {
        BigDecimal refundAmount = positive(amount);
        if (refundAmount.signum() == 0) {
            return;
        }
        recordTransaction(
                bookingId,
                AdminWalletTransactionType.BOOKING_REFUND,
                refundAmount,
                refundAmount.negate(),
                "Booking refund for booking " + bookingId
        );
    }

    @Override
    @LogExecution
    @TimedAction("ADMIN_WALLET_RECORD_WITHDRAWAL_PAYOUT")
    @AuditAction(action = "ADMIN_WALLET_RECORD_WITHDRAWAL_PAYOUT", resourceType = "ADMIN_WALLET")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWithdrawalPayout(UUID withdrawalRequestId, BigDecimal amount) {
        BigDecimal payoutAmount = positive(amount);
        recordTransaction(
                withdrawalRequestId,
                AdminWalletTransactionType.WITHDRAWAL_PAYOUT,
                payoutAmount,
                payoutAmount.negate(),
                "Manual withdrawal payout for request " + withdrawalRequestId
        );
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("ADMIN_GET_PLATFORM_WALLET")
    @AuditAction(action = "ADMIN_GET_PLATFORM_WALLET", resourceType = "ADMIN_WALLET")
    public AdminWalletResponse getPlatformWallet(Pageable pageable) {
        AdminWallet wallet = adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                .orElseGet(() -> AdminWallet.builder()
                        .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                        .balance(BigDecimal.ZERO)
                        .build());
        Page<AdminWalletTransactionResponse> transactions = adminWalletTransactionRepository
                .findByWalletIdOrderByCreatedAtDesc(AdminWallet.PLATFORM_ADMIN_WALLET_ID, pageable)
                .map(this::toResponse);
        return AdminWalletResponse.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .updatedAt(wallet.getUpdatedAt())
                .transactions(transactions)
                .build();
    }

    private void recordTransaction(UUID bookingId,
                                   AdminWalletTransactionType transactionType,
                                   BigDecimal amount,
                                   BigDecimal walletImpact,
                                   String description) {
        if (bookingId == null) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED, "Booking id is required.");
        }
        if (adminWalletTransactionRepository.findByBookingIdAndTransactionType(bookingId, transactionType).isPresent()) {
            log.info("[AdminWallet] Transaction already recorded bookingId={} type={}", bookingId, transactionType);
            return;
        }

        AdminWallet wallet = lockPlatformWallet();
        BigDecimal nextBalance = wallet.getBalance().add(walletImpact);
        if (nextBalance.signum() < 0) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED,
                    "Admin wallet balance is not enough for booking " + bookingId);
        }

        wallet.setBalance(nextBalance);
        adminWalletRepository.save(wallet);
        adminWalletTransactionRepository.save(AdminWalletTransaction.builder()
                .walletId(wallet.getId())
                .bookingId(bookingId)
                .transactionType(transactionType)
                .amount(amount)
                .walletImpact(walletImpact)
                .balanceAfter(nextBalance)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .build());
    }

    private AdminWallet lockPlatformWallet() {
        return adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                .orElseGet(() -> adminWalletRepository.save(AdminWallet.builder()
                        .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }

    private BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new AppException(BookingErrorCode.BOOKING_SETTLEMENT_FAILED, "Amount must be greater than zero.");
        }
        return amount;
    }

    private AdminWalletTransactionResponse toResponse(AdminWalletTransaction transaction) {
        return AdminWalletTransactionResponse.builder()
                .id(transaction.getId())
                .bookingId(transaction.getBookingId())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .walletImpact(transaction.getWalletImpact())
                .balanceAfter(transaction.getBalanceAfter())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
