package com.minduc.happabi.dto.response.admin;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class AdminWalletResponse {

    private String walletId;
    private BigDecimal balance;
    private Instant updatedAt;
    private Page<AdminWalletTransactionResponse> transactions;
}
