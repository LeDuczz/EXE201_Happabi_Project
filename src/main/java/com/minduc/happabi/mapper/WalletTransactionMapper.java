package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.TransactionDTO;
import com.minduc.happabi.entity.WalletTransaction;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class WalletTransactionMapper {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault());

  public TransactionDTO toTransactionDTO(WalletTransaction walletTransaction) {
    if (walletTransaction == null) {
      return null;
    }

    String description = "Other wallet transaction";
    String typeStr = walletTransaction.getTransactionType() != null ? walletTransaction.getTransactionType().name() : "";

    if (walletTransaction.getTransactionType() != null) {
      switch (walletTransaction.getTransactionType()) {
        case TOPUP_WALLET -> {
          description = "Wallet top-up";
        }
        case TOPUP_DEPOSIT -> {
          description = "Deposit top-up";
        }
        case BOOKING_EARNING -> {
          description = "Booking earning";
        }
        case FEE_DEDUCTION -> {
          description = "Commission deduction";
        }
        case PAYOUT -> {
          description = "Bank payout";
        }
      }
    }

    return TransactionDTO.builder()
      .id(walletTransaction.getId())
      .amount(walletTransaction.getAmount())
      .type(typeStr)
      .status(walletTransaction.getStatus() != null ? walletTransaction.getStatus().name() : null)
      .createdAt(walletTransaction.getCreatedAt() != null
        ? DATE_TIME_FORMATTER.format(walletTransaction.getCreatedAt())
        : null)
      .description(walletTransaction.getDescription() != null && !walletTransaction.getDescription().isBlank()
        ? walletTransaction.getDescription()
        : description)
      .build();
  }
}
