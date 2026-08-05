package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.AdminWalletTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminWalletLedgerServiceImplTest {

    @Mock
    private AdminWalletRepository adminWalletRepository;

    @Mock
    private AdminWalletTransactionRepository adminWalletTransactionRepository;

    @Mock
    private BookingRepository bookingRepository;

    private AdminWalletLedgerServiceImpl service;
    private AdminWallet wallet;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        service = new AdminWalletLedgerServiceImpl(adminWalletRepository, adminWalletTransactionRepository, bookingRepository);
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
        ArgumentCaptor<Specification<AdminWalletTransaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(adminWalletTransactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                org.mockito.ArgumentMatchers.eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(java.util.List.of(transaction)));
        when(bookingRepository.findAllByIdInWithPaymentRelations(java.util.Set.of(bookingId)))
                .thenReturn(java.util.List.of(buildBooking(bookingId)));

        Page<?> transactions = service.getPlatformWallet(Pageable.unpaged()).getTransactions();

        assertThat(transactions.getTotalElements()).isEqualTo(1);
        var response = (com.minduc.happabi.dto.response.admin.AdminWalletTransactionResponse) transactions.getContent().get(0);
        assertThat(response.getBooking()).isNotNull();
        assertThat(response.getBooking().getBookingKey()).isEqualTo("W9-PROD-001");
        assertThat(response.getBooking().getMotherName()).isEqualTo("Le Bao Ngoc");
        verify(adminWalletTransactionRepository).findAll(specCaptor.capture(), eq(Pageable.unpaged()));
        specCaptor.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
    }

    @Test
    void getPlatformWalletAppliesTransactionFilters() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-08T00:00:00Z");
        ArgumentCaptor<Specification<AdminWalletTransaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(adminWalletTransactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                eq(Pageable.unpaged())))
                .thenReturn(Page.empty());

        var response = service.getPlatformWallet(
                Pageable.unpaged(),
                " nurse_payout ",
                " out ",
                startAt,
                endAt);

        assertThat(response.getTransactions().getTotalElements()).isZero();
        verify(adminWalletTransactionRepository).findAll(specCaptor.capture(), eq(Pageable.unpaged()));
        specCaptor.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
    }

    @Test
    void getPlatformWalletAppliesIncomingDirectionFilterWithoutDates() {
        ArgumentCaptor<Specification<AdminWalletTransaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(adminWalletTransactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                eq(Pageable.unpaged())))
                .thenReturn(Page.empty());

        service.getPlatformWallet(Pageable.unpaged(), null, "IN", null, null);

        verify(adminWalletTransactionRepository).findAll(specCaptor.capture(), eq(Pageable.unpaged()));
        specCaptor.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
    }

    @Test
    void getPlatformWalletTreatsBlankFiltersAsMissing() {
        ArgumentCaptor<Specification<AdminWalletTransaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));
        when(adminWalletTransactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                eq(Pageable.unpaged())))
                .thenReturn(Page.empty());

        service.getPlatformWallet(Pageable.unpaged(), "   ", "   ", null, null);

        verify(adminWalletTransactionRepository).findAll(specCaptor.capture(), eq(Pageable.unpaged()));
        specCaptor.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
    }

    @Test
    void getPlatformWalletRejectsUnsupportedTransactionType() {
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.getPlatformWallet(
                Pageable.unpaged(),
                "unknown",
                null,
                null,
                null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unsupported admin wallet transaction type");

        verify(adminWalletTransactionRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                any(Pageable.class));
    }

    @Test
    void getPlatformWalletRejectsUnsupportedDirection() {
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.getPlatformWallet(
                Pageable.unpaged(),
                null,
                "SIDEWAYS",
                null,
                null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unsupported admin wallet transaction direction");

        verify(adminWalletTransactionRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<AdminWalletTransaction>>any(),
                any(Pageable.class));
    }

    private AdminWalletTransaction captureTransaction() {
        ArgumentCaptor<AdminWalletTransaction> captor = ArgumentCaptor.forClass(AdminWalletTransaction.class);
        verify(adminWalletTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Booking buildBooking(UUID id) {
        User mother = User.builder()
                .id(UUID.randomUUID())
                .fullName("Le Bao Ngoc")
                .phone("+84910000003")
                .email("mother03@happabi.local")
                .build();
        User nurseUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Pham Khanh Linh")
                .phone("+84367270392")
                .email("nurse01@happabi.local")
                .build();
        NurseProfile nurseProfile = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(nurseUser)
                .build();
        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(UUID.randomUUID())
                .serviceCode("W9_PRENATAL_RELAX_MASSAGE")
                .serviceType(ServiceOfferingType.SINGLE)
                .serviceName("Prenatal massage")
                .grossAmount(450000L)
                .platformFeeAmount(67500L)
                .nurseEarningAmount(382500L)
                .commissionRate(BigDecimal.valueOf(15))
                .build();
        return Booking.builder()
                .id(id)
                .bookingKey("W9-PROD-001")
                .status(BookingStatus.COMPLETED)
                .paymentOption(BookingPaymentOption.DEPOSIT_30_PERCENT)
                .mother(mother)
                .nurseProfile(nurseProfile)
                .serviceOffering(serviceOffering)
                .startAt(java.time.OffsetDateTime.now())
                .endAt(java.time.OffsetDateTime.now().plusHours(1))
                .grossAmount(450000L)
                .appPaymentAmount(135000L)
                .depositAmount(135000L)
                .remainingCashAmount(315000L)
                .platformFeeAmount(67500L)
                .nurseEarningAmount(382500L)
                .serviceAddress("S9.03 Vinhomes Grand Park")
                .build();
    }
}
