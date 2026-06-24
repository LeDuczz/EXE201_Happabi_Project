package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.dto.response.payment.BookingPaymentLinkResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.PaymentErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingPaymentTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import com.minduc.happabi.service.payment.PaymentGatewayFeeCalculator;
import com.minduc.happabi.service.nurse.NurseDepositPolicy;
import com.minduc.happabi.service.nurse.NurseWalletProvisioningService;
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
    private final PaymentGatewayFeeCalculator paymentGatewayFeeCalculator;
    private final BookingRepository bookingRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final NurseWalletProvisioningService nurseWalletProvisioningService;
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
    public String createTopUpPaymentLink(TopUpRequest request) {
        if (request.getTopUpType() != TransactionType.TOPUP_WALLET
                && request.getTopUpType() != TransactionType.TOPUP_DEPOSIT) {
            throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE,
                    "Unsupported nurse top-up transaction type.");
        }
        NurseProfile nurseProfile = currentNurseProfile();
        UUID nurseProfileId = nurseProfile.getId();
        nurseWalletProvisioningService.ensureWallet(nurseProfileId);

        List<WalletTransaction> pendingTransaction = walletTransactionRepository
                .findByNurseIdAndStatus(nurseProfileId, TransactionStatus.PENDING);

        pendingTransaction.stream()
                .filter(transaction -> transaction.getTransactionType() == request.getTopUpType())
                .forEach(transaction -> {
                transaction.setStatus(TransactionStatus.CANCELED);
                transaction.setDescription(CANCEL_TRANSACTION_FOR_CREATING_NEW_TRANSACTION_MESSAGE);
                });
        walletTransactionRepository.saveAll(pendingTransaction);

        WalletTransaction transaction = WalletTransaction.builder()
                .nurseId(nurseProfileId)
                .transactionType(request.getTopUpType())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .referenceId(nextWalletReferenceId())
                .build();
        walletTransactionRepository.save(transaction);

        return createWalletPaymentLink(transaction);
    }

    @LogExecution
    @AuditAction(action = "CREATE_NURSE_DEPOSIT_PAYMENT_LINK", resourceType = "NURSE_DEPOSIT")
    @TimedAction("CREATE_NURSE_DEPOSIT_PAYMENT_LINK")
    @Transactional
    @Override
    public String createNurseDepositPaymentLink() {
        NurseProfile nurseProfile = currentNurseProfile();
        if (nurseProfile.getNurseStatus() != NurseStatus.PENDING_DEPOSIT) {
            throw new AppException(PaymentErrorCode.BOOKING_PAYMENT_NOT_PAYABLE,
                    "Nurse deposit payment is not available for the current nurse status.");
        }
        nurseWalletProvisioningService.ensureWallet(nurseProfile.getId());

        List<WalletTransaction> pendingTransactions = walletTransactionRepository
                .findByNurseIdAndStatus(nurseProfile.getId(), TransactionStatus.PENDING);
        pendingTransactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.TOPUP_DEPOSIT)
                .forEach(transaction -> {
                    transaction.setStatus(TransactionStatus.CANCELED);
                    transaction.setDescription(CANCEL_TRANSACTION_FOR_CREATING_NEW_TRANSACTION_MESSAGE);
                });
        walletTransactionRepository.saveAll(pendingTransactions);

        WalletTransaction transaction = walletTransactionRepository.save(WalletTransaction.builder()
                .nurseId(nurseProfile.getId())
                .transactionType(TransactionType.TOPUP_DEPOSIT)
                .amount(NurseDepositPolicy.MINIMUM_DEPOSIT_AMOUNT)
                .status(TransactionStatus.PENDING)
                .referenceId(nextWalletReferenceId())
                .description("Required nurse deposit payment")
                .build());

        return createWalletPaymentLink(transaction);
    }

    private String createWalletPaymentLink(WalletTransaction transaction) {
        CreatePaymentLinkRequest paymentLinkRequest = CreatePaymentLinkRequest.builder()
                .orderCode(transaction.getReferenceId())
                .amount(transaction.getAmount().longValue())
                .description("HAPPABI" + transaction.getReferenceId())
                .returnUrl(returnUrlSuccess)
                .cancelUrl(returnUrlCancel)
                .expiredAt(Instant.now().getEpochSecond() + (30 * 60)) //expire at 30 minute
                .build();
        try {
            CreatePaymentLinkResponse paymentLinkResponse = payOS.paymentRequests().create(paymentLinkRequest);
            log.info("[PayOSPayment] Created nurse top-up payment link nurseProfileId={} orderCode={} amount={}",
                    transaction.getNurseId(), transaction.getReferenceId(), transaction.getAmount());
            return paymentLinkResponse.getCheckoutUrl();
        } catch (Exception e) {
            log.warn("[PayOSPayment] Failed to create nurse top-up payment link nurseProfileId={} orderCode={}",
                    transaction.getNurseId(), transaction.getReferenceId());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setDescription("Failed to create PayOS link: " + e.getMessage());
            walletTransactionRepository.save(transaction);
            throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE, e.getMessage());
        }
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private long nextWalletReferenceId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long referenceId = (Instant.now().toEpochMilli() % 1_000_000_000_000L) * 100
                    + ThreadLocalRandom.current().nextLong(100);
            if (!walletTransactionRepository.existsByReferenceId(referenceId)) {
                return referenceId;
            }
        }
        throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE,
                "Could not allocate unique PayOS transaction id.");
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

        PaymentGatewayFeeCalculator.GatewayFeeQuote feeQuote = paymentGatewayFeeCalculator
                .quote(booking.getAppPaymentAmount());
        BookingPaymentTransaction transaction = BookingPaymentTransaction.builder()
                .booking(booking)
                .transactionId(nextUniqueTransactionId())
                .amount(booking.getAppPaymentAmount())
                .providerFeeRate(feeQuote.rate())
                .providerFeeAmount(feeQuote.feeAmount())
                .netReceivedAmount(feeQuote.netReceivedAmount())
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
