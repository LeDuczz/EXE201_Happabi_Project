package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.NurseDepositActivationService;
import com.minduc.happabi.service.worksession.IWorkSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOsWebHookServiceTest {

    @Mock
    private PayOS payOS;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private BookingPaymentTransactionRepository bookingPaymentTransactionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSlotRepository bookingSlotRepository;

    @Mock
    private NurseWalletRepository nurseWalletRepository;

    @Mock
    private IWorkSessionService workSessionService;

    @Mock
    private IAdminWalletLedgerService adminWalletLedgerService;

    @Mock
    private INotificationPublisher notificationPublisher;

    @Mock
    private NurseDepositActivationService nurseDepositActivationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PayOsWebHookService service;

    @BeforeEach
    void setUp() {
        service = new PayOsWebHookService(
                payOS,
                walletTransactionRepository,
                bookingPaymentTransactionRepository,
                bookingRepository,
                bookingSlotRepository,
                nurseWalletRepository,
                workSessionService,
                adminWalletLedgerService,
                notificationPublisher,
                nurseDepositActivationService,
                eventPublisher);
    }

    @Test
    void cancelledBookingPaymentWebhookCancelsBookingAndReleasesSlot() {
        UUID bookingId = UUID.randomUUID();
        Long orderCode = 78592851056659L;
        BookingPaymentTransaction transaction = bookingPaymentTransaction(bookingId, orderCode);
        WebhookData data = webhookData(orderCode, "01", "Payment cancelled by customer");

        when(bookingPaymentTransactionRepository.markStatusIfPending(
                eq(orderCode),
                eq(TransactionStatus.PENDING),
                eq(TransactionStatus.CANCELED),
                any(OffsetDateTime.class),
                eq("FAIL TO PAYMENT: Payment cancelled by customer")))
                .thenReturn(1);
        when(bookingRepository.cancelActivePendingPayment(
                eq(bookingId),
                eq(BookingStatus.PENDING_PAYMENT),
                eq(BookingStatus.CANCELLED),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)))
                .thenReturn(1);

        String response = ReflectionTestUtils.invokeMethod(
                service, "handleBookingPaymentWebhook", data, transaction);

        assertThat(response).isEqualTo("Success to handle cancelled PayOs booking payment webhook");
        verify(bookingSlotRepository).releaseByBookingId(bookingId, BookingSlotStatus.AVAILABLE);
        verify(notificationPublisher).publish(
                eq(transaction.getBooking().getMother().getId()),
                eq(NotificationType.BOOKING_PAYMENT_CANCELLED),
                eq("Booking payment cancelled"),
                eq("Your booking for Newborn bath was cancelled because payment was cancelled."),
                eq("BOOKING"),
                eq(bookingId.toString()));
    }

    @Test
    void failedBookingPaymentWebhookKeepsBookingPendingForRetry() {
        UUID bookingId = UUID.randomUUID();
        Long orderCode = 78592851056660L;
        BookingPaymentTransaction transaction = bookingPaymentTransaction(bookingId, orderCode);
        WebhookData data = webhookData(orderCode, "99", "Bank rejected payment");

        when(bookingPaymentTransactionRepository.markStatusIfPending(
                eq(orderCode),
                eq(TransactionStatus.PENDING),
                eq(TransactionStatus.FAILED),
                any(OffsetDateTime.class),
                eq("FAIL TO PAYMENT: Bank rejected payment")))
                .thenReturn(1);

        String response = ReflectionTestUtils.invokeMethod(
                service, "handleBookingPaymentWebhook", data, transaction);

        assertThat(response).isEqualTo("Success to handle failed PayOs booking payment webhook");
        verify(bookingRepository, never()).cancelActivePendingPayment(any(), any(), any(), any(), any());
        verify(bookingSlotRepository, never()).releaseByBookingId(any(), any());
        verify(notificationPublisher).publish(
                eq(transaction.getBooking().getMother().getId()),
                eq(NotificationType.BOOKING_PAYMENT_FAILED),
                eq("Booking payment was not completed"),
                any(),
                eq("BOOKING"),
                eq(bookingId.toString()));
    }

    private BookingPaymentTransaction bookingPaymentTransaction(UUID bookingId, Long orderCode) {
        Booking booking = Booking.builder()
                .id(bookingId)
                .mother(User.builder().id(UUID.randomUUID()).build())
                .serviceOffering(ServiceOffering.builder().serviceName("Newborn bath").build())
                .paymentExpiresAt(OffsetDateTime.now().plusMinutes(10))
                .build();
        return BookingPaymentTransaction.builder()
                .booking(booking)
                .transactionId(orderCode)
                .amount(117000L)
                .status(TransactionStatus.PENDING)
                .build();
    }

    private WebhookData webhookData(Long orderCode, String code, String description) {
        return WebhookData.builder()
                .orderCode(orderCode)
                .amount(117000L)
                .description("Booking payment")
                .accountNumber("123456789")
                .reference("PAYOS-" + orderCode)
                .transactionDateTime("2026-08-05 10:00:00")
                .currency("VND")
                .paymentLinkId("payos-link-" + orderCode)
                .code(code)
                .desc(description)
                .build();
    }
}
