package com.minduc.happabi.schedule;

import com.minduc.happabi.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionCleanupJob {
    private final WalletTransactionRepository walletTransactionRepository;




}
