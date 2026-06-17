package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.service.notification.INotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingPaymentCleanupService {

    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingPaymentTransactionRepository bookingPaymentTransactionRepository;
    private final INotificationPublisher notificationPublisher;

    @Scheduled(fixedDelayString = "${app.booking.payment-cleanup-fixed-delay-ms:60000}")
    @LogExecution
    @TimedAction("CLEANUP_EXPIRED_BOOKING_PAYMENTS")
    @AuditAction(action = "CLEANUP_EXPIRED_BOOKING_PAYMENTS", resourceType = "BOOKING_PAYMENT")
    @Transactional
    public void cleanupExpiredPendingPayments() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> bookingIds = bookingRepository.findExpiredPendingPaymentIds(
                BookingStatus.PENDING_PAYMENT, now, PageRequest.of(0, BATCH_SIZE));
        for (UUID bookingId : bookingIds) {
            cleanupBooking(bookingId, now);
        }
    }

    private void cleanupBooking(UUID bookingId, OffsetDateTime now) {
        int cancelled = bookingRepository.cancelExpiredPendingPayment(
                bookingId,
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.CANCELLED,
                now,
                now);
        if (cancelled != 1) {
            return;
        }

        int releasedSlots = bookingSlotRepository.releaseByBookingId(bookingId, BookingSlotStatus.AVAILABLE);
        int cancelledTransactions = bookingPaymentTransactionRepository.markBookingTransactionsStatus(
                bookingId,
                TransactionStatus.PENDING,
                TransactionStatus.CANCELED,
                "Booking payment expired",
                now);
        notifyMotherPaymentExpired(bookingId);
        log.info("[BookingPaymentCleanup] Cancelled expired booking id={}, releasedSlots={}, cancelledTransactions={}",
                bookingId, releasedSlots, cancelledTransactions);
    }

    private void notifyMotherPaymentExpired(UUID bookingId) {
        bookingRepository.findByIdWithPaymentRelations(bookingId)
                .ifPresent(booking -> notificationPublisher.publish(
                        booking.getMother().getId(),
                        NotificationType.BOOKING_PAYMENT_EXPIRED,
                        "Booking payment expired",
                        "Your booking for %s was cancelled because payment was not completed in time."
                                .formatted(booking.getServiceOffering().getServiceName()),
                        "BOOKING",
                        booking.getId().toString()
                ));
    }
}
