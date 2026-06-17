package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.dto.response.payment.BookingPaymentLinkResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.PaymentErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOsPaymentService implements IPayOsPaymentService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final BookingPaymentTransactionRepository bookingPaymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final PayOS payOS;

    @Value("${payos.return-url-success}")
    private String returnUrlSuccess;

    @Value("${payos.return-url-cancel}")
    private String returnUrlCancel;

    private static final String CANCEL_TRANSACTION_FOR_CREATING_NEW_TRANSACTION_MESSAGE = "Automatically canceled due to creation of a new transaction";

    @LogExecution
    @AuditAction(action = "CREATE_NURSE_TOP_UP_PAYMENT_LINK", resourceType = "WALLET_TRANSACTION")
    @TimedAction("CREATE_NURSE_TOP_UP_PAYMENT_LINK")
    @Transactional
    @Override
    public String createTopUpPaymentLink(String nurseId, TopUpRequest request) {

        List<WalletTransaction> pendingTransaction = walletTransactionRepository
                .findByNurseIdAndStatus(UUID.fromString(nurseId), TransactionStatus.PENDING);

        if (!pendingTransaction.isEmpty()) {
            for (WalletTransaction transaction : pendingTransaction) {
                transaction.setStatus(TransactionStatus.CANCELED);
                transaction.setDescription(CANCEL_TRANSACTION_FOR_CREATING_NEW_TRANSACTION_MESSAGE);
            }
            walletTransactionRepository.saveAll(pendingTransaction);

        }


        long orderCode = Instant.now().getEpochSecond() % 1000000000L;

        WalletTransaction transaction = WalletTransaction.builder()
                .nurseId(UUID.fromString(nurseId))
                .transactionType(request.getTopUpType())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .referenceId(orderCode)
                .build();
        walletTransactionRepository.save(transaction);

        CreatePaymentLinkRequest paymentLinkRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(request.getAmount().longValue())
                .description("HAPPABI" + orderCode)
                .returnUrl(returnUrlSuccess)
                .cancelUrl(returnUrlCancel)
                .expiredAt(Instant.now().getEpochSecond() + (30 * 60)) //expire at 30 minute
                .build();
        try {
            CreatePaymentLinkResponse paymentLinkResponse = payOS.paymentRequests().create(paymentLinkRequest);
            log.info("[PayOSPayment] Created nurse top-up payment link nurseId={} orderCode={} amount={}",
                    nurseId, orderCode, request.getAmount());
            return paymentLinkResponse.getCheckoutUrl();
        } catch (Exception e) {
            log.warn("[PayOSPayment] Failed to create nurse top-up payment link nurseId={} orderCode={}",
                    nurseId, orderCode);
            throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE, e.getMessage());
        }
    }

    @LogExecution
    @AuditAction(action = "CREATE_BOOKING_PAYMENT_LINK", resourceType = "BOOKING_PAYMENT")
    @TimedAction("CREATE_BOOKING_PAYMENT_LINK")
    @Transactional
    @Override
    public BookingPaymentLinkResponse createBookingPaymentLink(UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndMotherIdForUpdate(
                        bookingId, userAccountLookupService.getCurrentUser().getId())
                .orElseThrow(() -> new AppException(PaymentErrorCode.BOOKING_PAYMENT_NOT_FOUND));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_NOT_PAYABLE);
        }
        if (!booking.getPaymentExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_EXPIRED);
        }
        if (booking.getAppPaymentAmount() == null || booking.getAppPaymentAmount() <= 0) {
            throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_AMOUNT_INVALID);
        }

        Optional<BookingPaymentTransaction> existingPending = bookingPaymentTransactionRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), TransactionStatus.PENDING)
                .filter(transaction -> transaction.getCheckoutUrl() != null && !transaction.getCheckoutUrl().isBlank());
        if (existingPending.isPresent()) {
            log.info("[PayOSPayment] Reused pending booking payment link bookingId={} transactionId={}",
                    bookingId, existingPending.get().getTransactionId());
            return toBookingPaymentLinkResponse(existingPending.get(), booking);
        }

        BookingPaymentTransaction transaction = BookingPaymentTransaction.builder()
                .booking(booking)
                .transactionId(nextUniqueTransactionId())
                .amount(booking.getAppPaymentAmount())
                .status(TransactionStatus.PENDING)
                .description("Booking payment pending")
                .build();
        try {
            bookingPaymentTransactionRepository.saveAndFlush(transaction);

            CreatePaymentLinkRequest paymentLinkRequest = CreatePaymentLinkRequest.builder()
                    .orderCode(transaction.getTransactionId())
                    .amount(transaction.getAmount())
                    .description("BOOKING" + transaction.getTransactionId())
                    .returnUrl(returnUrlSuccess)
                    .cancelUrl(returnUrlCancel)
                    .expiredAt(booking.getPaymentExpiresAt().toEpochSecond())
                    .build();
            CreatePaymentLinkResponse paymentLinkResponse = payOS.paymentRequests().create(paymentLinkRequest);
            transaction.setCheckoutUrl(paymentLinkResponse.getCheckoutUrl());
            transaction = bookingPaymentTransactionRepository.save(transaction);
            log.info("[PayOSPayment] Created booking payment link bookingId={} transactionId={} amount={}",
                    bookingId, transaction.getTransactionId(), transaction.getAmount());
            return toBookingPaymentLinkResponse(transaction, booking);
        } catch (DataIntegrityViolationException e) {
            log.warn("[PayOSPayment] Booking payment transaction conflict bookingId={}", bookingId);
            throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_NOT_PAYABLE, e);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setDescription("Failed to create PayOS link: " + e.getMessage());
            bookingPaymentTransactionRepository.save(transaction);
            log.warn("[PayOSPayment] Failed to create booking payment link bookingId={} transactionId={}",
                    bookingId, transaction.getTransactionId());
            throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE, e.getMessage());
        }
    }

    private Long nextUniqueTransactionId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long transactionId = (Instant.now().toEpochMilli() % 1_000_000_000_000L) * 100
                    + ThreadLocalRandom.current().nextLong(100);
            if (!bookingPaymentTransactionRepository.existsByTransactionId(transactionId)) {
                return transactionId;
            }
        }
        throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_NOT_PAYABLE,
                "Could not allocate unique PayOS transaction id.");
    }

    private BookingPaymentLinkResponse toBookingPaymentLinkResponse(BookingPaymentTransaction transaction,
                                                                    Booking booking) {
        return BookingPaymentLinkResponse.builder()
                .bookingId(booking.getId())
                .transactionId(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .checkoutUrl(transaction.getCheckoutUrl())
                .paymentExpiresAt(booking.getPaymentExpiresAt())
                .build();
    }
}
