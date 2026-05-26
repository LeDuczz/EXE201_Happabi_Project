package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.PaymentErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayOsPaymentService implements IPayOsPaymentService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final PayOS payOS;

    @Value("${payos.return-url-success}")
    private String returnUrlSuccess;

    @Value("${payos.return-url-cancel}")
    private String returnUrlCancel;

    private final static String CANCEL_TRANSACTION_FOR_CREATING_NEW_TRANSACTION_MESSAGE = "Automatically canceled due to creation of a new transaction";

    @LogExecution
    @AuditAction(action = "PAY", resourceType = "WALLET_TRACTION")
    @TimedAction("Nurse_request_payment")
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
            return paymentLinkResponse.getCheckoutUrl();
        } catch (Exception e) {
            throw new AppException(PaymentErrorCode.FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE, e.getMessage());
        }
    }
}
