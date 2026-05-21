package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PayOsPaymentService implements IPayOsPaymentService {

    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    @Override
    public String createTopUpPaymentLink(String nurseId, TopUpRequest request) {
        String orderCode = "PAYOS - " + Instant.now().getEpochSecond();

        WalletTransaction transaction = WalletTransaction.builder()
                .nurseId(nurseId)
                .transactionType(request.getTopUpType())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .referenceId(orderCode)
                .build();
        walletTransactionRepository.save(transaction);


        return "";
    }
}
