package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.AdminWalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWalletLedgerServiceImplTest {

    @Mock
    private AdminWalletRepository adminWalletRepository;

    @Mock
    private AdminWalletTransactionRepository adminWalletTransactionRepository;

    private AdminWalletLedgerServiceImpl service;
    private AdminWallet wallet;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        service = new AdminWalletLedgerServiceImpl(adminWalletRepository, adminWalletTransactionRepository);
        wallet = AdminWallet.builder()
                .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                .balance(BigDecimal.ZERO)
                .build();
        bookingId = UUID.randomUUID();
    }

    @Test
    void recordBookingPaymentReceivedCreditsAdminWalletOnce() {
        when(adminWalletTransactionRepository.findByBookingIdAndTransactionType(
                bookingId, AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED)).thenReturn(Optional.empty());
        when(adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        service.recordBookingPaymentReceived(bookingId, BigDecimal.valueOf(135000));

        assertThat(wallet.getBalance()).isEqualByComparingTo("135000");
        AdminWalletTransaction transaction = captureTransaction();
        assertThat(transaction.getTransactionType()).isEqualTo(AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED);
        assertThat(transaction.getAmount()).isEqualByComparingTo("135000");
        assertThat(transaction.getWalletImpact()).isEqualByComparingTo("135000");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("135000");
    }

    @Test
    void recordNursePayoutDebitsAdminWallet() {
        wallet.setBalance(BigDecimal.valueOf(135000));
        when(adminWalletTransactionRepository.findByBookingIdAndTransactionType(
                bookingId, AdminWalletTransactionType.NURSE_PAYOUT)).thenReturn(Optional.empty());
        when(adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        service.recordNursePayout(bookingId, BigDecimal.valueOf(67500));

        assertThat(wallet.getBalance()).isEqualByComparingTo("67500");
        AdminWalletTransaction transaction = captureTransaction();
        assertThat(transaction.getTransactionType()).isEqualTo(AdminWalletTransactionType.NURSE_PAYOUT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("67500");
        assertThat(transaction.getWalletImpact()).isEqualByComparingTo("-67500");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("67500");
    }

    @Test
    void recordPaymentGatewayFeeDebitsAdminWalletOnce() {
        wallet.setBalance(BigDecimal.valueOf(135000));
        when(adminWalletTransactionRepository.findByBookingIdAndTransactionType(
                bookingId, AdminWalletTransactionType.PAYMENT_GATEWAY_FEE)).thenReturn(Optional.empty());
        when(adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        service.recordPaymentGatewayFee(bookingId, BigDecimal.valueOf(743));

        assertThat(wallet.getBalance()).isEqualByComparingTo("134257");
        AdminWalletTransaction transaction = captureTransaction();
        assertThat(transaction.getTransactionType()).isEqualTo(AdminWalletTransactionType.PAYMENT_GATEWAY_FEE);
        assertThat(transaction.getWalletImpact()).isEqualByComparingTo("-743");
    }

    @Test
    void recordWithdrawalPayoutDebitsAdminWallet() {
        wallet.setBalance(BigDecimal.valueOf(1000000));
        when(adminWalletTransactionRepository.findByBookingIdAndTransactionType(
                bookingId, AdminWalletTransactionType.WITHDRAWAL_PAYOUT)).thenReturn(Optional.empty());
        when(adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        service.recordWithdrawalPayout(bookingId, BigDecimal.valueOf(250000));

        assertThat(wallet.getBalance()).isEqualByComparingTo("750000");
        AdminWalletTransaction transaction = captureTransaction();
        assertThat(transaction.getTransactionType()).isEqualTo(AdminWalletTransactionType.WITHDRAWAL_PAYOUT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("250000");
        assertThat(transaction.getWalletImpact()).isEqualByComparingTo("-250000");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("750000");
    }

    @Test
    void recordBookingPaymentReceivedRejectsZeroAmount() {
        assertThatThrownBy(() -> service.recordBookingPaymentReceived(bookingId, BigDecimal.ZERO))
                .isInstanceOf(AppException.class);

        verify(adminWalletRepository, never()).findByIdForUpdate(any());
        verify(adminWalletTransactionRepository, never()).save(any());
    }

    @Test
    void recordTransactionDoesNothingWhenLedgerAlreadyExists() {
        when(adminWalletTransactionRepository.findByBookingIdAndTransactionType(
                bookingId, AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED))
                .thenReturn(Optional.of(AdminWalletTransaction.builder().bookingId(bookingId).build()));

        service.recordBookingPaymentReceived(bookingId, BigDecimal.valueOf(135000));

        verify(adminWalletRepository, never()).findByIdForUpdate(any());
        verify(adminWalletTransactionRepository, never()).save(any());
    }

    @Test
    void getPlatformWalletReturnsBalanceAndPagedTransactions() {
        AdminWalletTransaction transaction = AdminWalletTransaction.builder()
                .bookingId(bookingId)
                .transactionType(AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED)
                .amount(BigDecimal.valueOf(135000))
                .walletImpact(BigDecimal.valueOf(135000))
                .balanceAfter(BigDecimal.valueOf(135000))
                .build();
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(adminWalletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(
                AdminWallet.PLATFORM_ADMIN_WALLET_ID, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(java.util.List.of(transaction)));

        Page<?> transactions = service.getPlatformWallet(Pageable.unpaged()).getTransactions();

        assertThat(transactions.getTotalElements()).isEqualTo(1);
    }

    private AdminWalletTransaction captureTransaction() {
        ArgumentCaptor<AdminWalletTransaction> captor = ArgumentCaptor.forClass(AdminWalletTransaction.class);
        verify(adminWalletTransactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
