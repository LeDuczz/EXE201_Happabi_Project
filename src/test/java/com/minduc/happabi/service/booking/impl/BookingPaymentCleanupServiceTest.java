package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.observability.audit.AuditRecorder;
import com.minduc.happabi.service.notification.INotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPaymentCleanupServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSlotRepository bookingSlotRepository;

    @Mock
    private BookingPaymentTransactionRepository bookingPaymentTransactionRepository;

    @Mock
    private INotificationPublisher notificationPublisher;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private BookingPaymentCleanupService service;

    @Test
    void cleanupExpiredPendingPaymentsCancelsBookingReleasesSlotsAndCancelsPaymentTransactions() {
        UUID bookingId = UUID.randomUUID();
        var booking = com.minduc.happabi.entity.Booking.builder()
                .id(bookingId)
                .mother(com.minduc.happabi.entity.User.builder().id(UUID.randomUUID()).build())
                .serviceOffering(com.minduc.happabi.entity.ServiceOffering.builder().serviceName("Newborn bath").build())
                .build();
        when(bookingRepository.findExpiredPendingPaymentIds(eq(BookingStatus.PENDING_PAYMENT), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(bookingId));
        when(bookingRepository.cancelExpiredPendingPayment(eq(bookingId), eq(BookingStatus.PENDING_PAYMENT),
                eq(BookingStatus.CANCELLED), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(1);
        when(bookingRepository.findByIdWithPaymentRelations(bookingId)).thenReturn(Optional.of(booking));

        service.cleanupExpiredPendingPayments();

        verify(bookingSlotRepository).releaseByBookingId(bookingId, BookingSlotStatus.AVAILABLE);
        verify(bookingPaymentTransactionRepository).markBookingTransactionsStatus(
                eq(bookingId),
                eq(TransactionStatus.PENDING),
                eq(TransactionStatus.CANCELED),
                eq("Booking payment expired"),
                any(OffsetDateTime.class));
        verify(notificationPublisher).publish(
                eq(booking.getMother().getId()),
                eq(NotificationType.BOOKING_PAYMENT_EXPIRED),
                any(),
                any(),
                eq("BOOKING"),
                eq(bookingId.toString()));
    }

    @Test
    void cleanupExpiredPendingPaymentsDoesNotReleaseSlotsWhenAtomicCancelLosesRace() {
        UUID bookingId = UUID.randomUUID();
        ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        when(bookingRepository.findExpiredPendingPaymentIds(eq(BookingStatus.PENDING_PAYMENT), nowCaptor.capture(), any(Pageable.class)))
                .thenReturn(List.of(bookingId));
        when(bookingRepository.cancelExpiredPendingPayment(eq(bookingId), eq(BookingStatus.PENDING_PAYMENT),
                eq(BookingStatus.CANCELLED), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(0);

        service.cleanupExpiredPendingPayments();

        assertThat(nowCaptor.getValue()).isNotNull();
        verify(bookingSlotRepository, never()).releaseByBookingId(any(), any());
        verify(bookingPaymentTransactionRepository, never()).markBookingTransactionsStatus(any(), any(), any(), any(), any());
    }
}
