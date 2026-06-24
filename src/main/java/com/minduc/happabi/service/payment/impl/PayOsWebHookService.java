package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.dto.event.BusinessMetricRequestedEvent;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import com.minduc.happabi.service.nurse.NurseDepositActivationService;
import com.minduc.happabi.service.payment.IPayOsWebhookService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.worksession.IWorkSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOsWebHookService implements IPayOsWebhookService {

    private final PayOS payOS;
    private final WalletTransactionRepository walletTransactionRepository;
    private final BookingPaymentTransactionRepository bookingPaymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final NurseWalletRepository nurseWalletRepository;
    private final IWorkSessionService workSessionService;
    private final IAdminWalletLedgerService adminWalletLedgerService;
    private final INotificationPublisher notificationPublisher;
    private final NurseDepositActivationService nurseDepositActivationService;
    private final ApplicationEventPublisher eventPublisher;

    @LogExecution
    @TimedAction("HANDLE_PAYOS_WEBHOOK")
    @AuditAction(action = "HANDLE_PAYOS_WEBHOOK", resourceType = "PAYOS_WEBHOOK")
    @Transactional
    @Override
    public String handlePayOsWebhook(Webhook webhookBody) {
        try {
            WebhookData data = payOS.webhooks().verify(webhookBody);
            BookingPaymentTransaction bookingPayment = bookingPaymentTransactionRepository
                    .findByTransactionIdForUpdate(data.getOrderCode())
                    .orElse(null);
            if (bookingPayment != null) {
                return handleBookingPaymentWebhook(data, bookingPayment);
            }

            WalletTransaction transaction = walletTransactionRepository
                    .findByReferenceIdForUpdate(data.getOrderCode())
                    .orElse(null);
            if (transaction != null) {
                if (transaction.getStatus() != TransactionStatus.PENDING) {
                    log.info("[PayOSWebhook] Duplicate nurse wallet webhook orderCode={} status={}",
                            data.getOrderCode(), transaction.getStatus());
                    return "Nurse wallet webhook already processed";
                }
                if ("00".equals(data.getCode())) {
                    transaction.setStatus(TransactionStatus.SUCCESS);
                    NurseWallet nurseWallet = nurseWalletRepository.findByNurseIdForUpdate(transaction.getNurseId())
                            .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));

                    if (transaction.getTransactionType() == TransactionType.TOPUP_WALLET) {
                        nurseWallet.setBalance(nurseWallet.getBalance().add(transaction.getAmount()));
                        transaction.setWalletImpact(transaction.getAmount());
                    } else if (transaction.getTransactionType() == TransactionType.TOPUP_DEPOSIT) {
                        nurseWallet.setDepositBalance(nurseWallet.getDepositBalance().add(transaction.getAmount()));
                        transaction.setDepositImpact(transaction.getAmount());
                    }
                    nurseWalletRepository.save(nurseWallet);
                    walletTransactionRepository.save(transaction);

                    if (transaction.getTransactionType() == TransactionType.TOPUP_DEPOSIT) {
                        nurseDepositActivationService.activateIfDepositRequirementMet(transaction.getNurseId());
                    }

                    eventPublisher.publishEvent(new BusinessMetricRequestedEvent(
                            UUID.randomUUID(),
                            "TRANSACTION_SUCCESS",
                            Instant.now(),
                            transaction.getAmount(),
                            "SUCCESS"
                    ));
                    log.info("[PayOSWebhook] Nurse wallet top-up success orderCode={} nurseId={} amount={}",
                            data.getOrderCode(), transaction.getNurseId(), transaction.getAmount());

                } else {
                    transaction.setStatus(TransactionStatus.FAILED);
                    transaction.setDescription("FAIL TO PAYMENT: " + data.getDesc());
                    walletTransactionRepository.save(transaction);
                    log.warn("[PayOSWebhook] Nurse wallet top-up failed orderCode={} reason={}",
                            data.getOrderCode(), data.getDesc());
                }

            }
            return "Success to handle PayOsWebhook";

        } catch (Exception e) {
            throw new AppException(NurseWalletErrorCode.DATA_WEBHOOK_ERROR, e.getMessage());
        }

    }

    private String handleBookingPaymentWebhook(WebhookData data, BookingPaymentTransaction bookingPayment) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID bookingId = bookingPayment.getBooking().getId();
        if ("00".equals(data.getCode())) {
            int transactionUpdated = bookingPaymentTransactionRepository.markStatusIfPending(
                    data.getOrderCode(),
                    TransactionStatus.PENDING,
                    TransactionStatus.SUCCESS,
                    now,
                    "PayOS payment success");
            if (transactionUpdated == 0) {
                log.info("[PayOSWebhook] Duplicate booking payment success webhook orderCode={}", data.getOrderCode());
                return "Booking payment webhook already processed";
            }

            int bookingUpdated = bookingRepository.markPaidIfPendingAndNotExpired(
                    bookingId,
                    BookingStatus.PENDING_PAYMENT,
                    BookingStatus.ACCEPTED,
                    now,
                    now);
            if (bookingUpdated == 1) {
                Booking booking = bookingRepository.findByIdWithPaymentRelations(bookingId)
                        .orElseThrow(() -> new AppException(BookingErrorCode.BOOKING_CREATE_FAILED));
                adminWalletLedgerService.recordBookingPaymentReceived(
                        bookingId,
                        BigDecimal.valueOf(bookingPayment.getAmount()));
                long providerFeeAmount = bookingPayment.getProviderFeeAmount() == null
                        ? 0L
                        : bookingPayment.getProviderFeeAmount();
                if (providerFeeAmount > 0) {
                    adminWalletLedgerService.recordPaymentGatewayFee(
                            bookingId,
                            BigDecimal.valueOf(providerFeeAmount));
                }
                workSessionService.createFromAcceptedBooking(booking);
                eventPublisher.publishEvent(new BusinessMetricRequestedEvent(
                        UUID.randomUUID(),
                        "BOOKING_PAYMENT_SUCCESS",
                        Instant.now(),
                        BigDecimal.valueOf(bookingPayment.getAmount()),
                        "SUCCESS"
                ));
                log.info("[PayOSWebhook] Booking payment success bookingId={} orderCode={} grossAmount={} providerFee={} netReceived={}",
                        bookingId, data.getOrderCode(), bookingPayment.getAmount(), providerFeeAmount,
                        bookingPayment.getNetReceivedAmount());
            }
            return "Success to handle PayOs booking payment webhook";
        }

        int transactionUpdated = bookingPaymentTransactionRepository.markStatusIfPending(
                data.getOrderCode(),
                TransactionStatus.PENDING,
                TransactionStatus.FAILED,
                now,
                "FAIL TO PAYMENT: " + data.getDesc());
        if (transactionUpdated == 0) {
            log.info("[PayOSWebhook] Duplicate booking payment failed webhook orderCode={}", data.getOrderCode());
            return "Booking payment webhook already processed";
        }
        notifyMotherPaymentFailed(bookingPayment, data.getDesc());
        log.warn("[PayOSWebhook] Booking payment failed bookingId={} orderCode={} reason={}",
                bookingId, data.getOrderCode(), data.getDesc());
        return "Success to handle failed PayOs booking payment webhook";
    }

    private void notifyMotherPaymentFailed(BookingPaymentTransaction bookingPayment, String reason) {
        Booking booking = bookingPayment.getBooking();
        notificationPublisher.publish(
                booking.getMother().getId(),
                NotificationType.BOOKING_PAYMENT_FAILED,
                "Booking payment was not completed",
                "Payment for %s was not completed%s. You can try again before %s."
                        .formatted(
                                booking.getServiceOffering().getServiceName(),
                                reason == null || reason.isBlank() ? "" : ": " + reason,
                                booking.getPaymentExpiresAt().toLocalTime()),
                "BOOKING",
                booking.getId().toString()
        );
    }
}

