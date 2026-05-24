package com.minduc.happabi.service.payment.impl;

import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.payment.IPayOsWebhookService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@Service
@RequiredArgsConstructor
public class PayOsWebHookService implements IPayOsWebhookService {

    private final PayOS payOS;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NurseWalletRepository nurseWalletRepository;

    @LogExecution
    @AuditAction(action = "HANDLE", resourceType = "WALLET_TRANSACTION")
    @Transactional
    @Override
    public String handlePayOsWebhook(Webhook webhookBody) {
        try {
            WebhookData data = payOS.webhooks().verify(webhookBody);

            WalletTransaction transaction = walletTransactionRepository
                    .findByReferenceIdAndStatus(data.getOrderCode(), TransactionStatus.PENDING)
                    .orElse(null);
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                walletTransactionRepository.save(transaction);

                NurseWallet nurseWallet = nurseWalletRepository.findByNurseId(transaction.getNurseId())
                        .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));
                if (transaction.getTransactionType() == TransactionType.TOPUP_WALLET) {
                    nurseWallet.setBalance(nurseWallet.getBalance().add(transaction.getAmount()));
                } else if (transaction.getTransactionType() == TransactionType.TOPUP_DEPOSIT) {
                    nurseWallet.setDepositBalance(nurseWallet.getDepositBalance().add(transaction.getAmount()));
                }
                walletTransactionRepository.save(transaction);
            }
            return "Success to handle PayOsWebhook";

        } catch (Exception e) {
            return "Fail to handle PayOsWebhook";
        }

    }
}
