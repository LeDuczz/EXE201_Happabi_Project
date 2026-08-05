package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.nurse.NurseWalletProvisioningService;
import com.minduc.happabi.service.payment.PaymentGatewayFeeCalculator;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOsPaymentServiceTest {

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private BookingPaymentTransactionRepository bookingPaymentTransactionRepository;

    @Mock
    private PaymentGatewayFeeCalculator paymentGatewayFeeCalculator;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSlotRepository bookingSlotRepository;

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private UserAccountLookupService userAccountLookupService;

    @Mock
    private NurseWalletProvisioningService nurseWalletProvisioningService;

    @Mock
    private PayOS payOS;

    private PayOsPaymentService service;

    @BeforeEach
    void setUp() {
        service = new PayOsPaymentService(
                walletTransactionRepository,
                bookingPaymentTransactionRepository,
                paymentGatewayFeeCalculator,
                bookingRepository,
                bookingSlotRepository,
                nurseProfileRepository,
                userAccountLookupService,
                nurseWalletProvisioningService,
                payOS);
    }

    @Test
    void cancelBookingPaymentFromReturnCancelsPendingOwnedBookingAndReleasesSlot() {
        UUID motherId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Long orderCode = 78592851056659L;
        when(userAccountLookupService.getCurrentUser()).thenReturn(User.builder().id(motherId).build());
        when(bookingPaymentTransactionRepository.findByTransactionIdForUpdate(orderCode))
                .thenReturn(Optional.of(transaction(bookingId, motherId, TransactionStatus.PENDING)));
        when(bookingPaymentTransactionRepository.markStatusIfPending(
                eq(orderCode),
                eq(TransactionStatus.PENDING),
                eq(TransactionStatus.CANCELED),
                any(OffsetDateTime.class),
                eq("PayOS checkout cancelled by customer")))
                .thenReturn(1);
        when(bookingRepository.cancelActivePendingPayment(
                eq(bookingId),
                eq(BookingStatus.PENDING_PAYMENT),
                eq(BookingStatus.CANCELLED),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)))
                .thenReturn(1);

        service.cancelBookingPaymentFromReturn(orderCode);

        verify(bookingSlotRepository).releaseByBookingId(bookingId, BookingSlotStatus.AVAILABLE);
    }

    @Test
    void cancelBookingPaymentFromReturnDoesNotMutateTerminalTransaction() {
        UUID motherId = UUID.randomUUID();
        Long orderCode = 78592851056660L;
        when(userAccountLookupService.getCurrentUser()).thenReturn(User.builder().id(motherId).build());
        when(bookingPaymentTransactionRepository.findByTransactionIdForUpdate(orderCode))
                .thenReturn(Optional.of(transaction(UUID.randomUUID(), motherId, TransactionStatus.SUCCESS)));

        service.cancelBookingPaymentFromReturn(orderCode);

        verify(bookingPaymentTransactionRepository, never()).markStatusIfPending(any(), any(), any(), any(), any());
        verify(bookingRepository, never()).cancelActivePendingPayment(any(), any(), any(), any(), any());
        verify(bookingSlotRepository, never()).releaseByBookingId(any(), any());
    }

    @Test
    void cancelBookingPaymentFromReturnRejectsDifferentMother() {
        UUID bookingOwnerId = UUID.randomUUID();
        Long orderCode = 78592851056661L;
        when(userAccountLookupService.getCurrentUser()).thenReturn(User.builder().id(UUID.randomUUID()).build());
        when(bookingPaymentTransactionRepository.findByTransactionIdForUpdate(orderCode))
                .thenReturn(Optional.of(transaction(UUID.randomUUID(), bookingOwnerId, TransactionStatus.PENDING)));

        assertThatThrownBy(() -> service.cancelBookingPaymentFromReturn(orderCode))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(BookingErrorCode.BOOKING_ACCESS_DENIED.getMessage());
    }

    private BookingPaymentTransaction transaction(UUID bookingId, UUID motherId, TransactionStatus status) {
        Booking booking = Booking.builder()
                .id(bookingId)
                .mother(User.builder().id(motherId).build())
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentExpiresAt(OffsetDateTime.now().plusMinutes(10))
                .build();
        return BookingPaymentTransaction.builder()
                .booking(booking)
                .transactionId(78592851056659L)
                .amount(117000L)
                .status(status)
                .build();
    }
}
