package com.minduc.happabi.dto.response.admin;

import com.minduc.happabi.enums.AdminWalletTransactionType;
import com.minduc.happabi.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminWalletTransactionResponse {

    private UUID id;
    private UUID bookingId;
    private AdminWalletTransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal walletImpact;
    private BigDecimal balanceAfter;
    private TransactionStatus status;
    private String description;
    private Instant createdAt;
}
