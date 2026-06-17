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

    String description = "Giao dich khac";
    String typeStr = "";

    if (walletTransaction.getTransactionType() != null) {
      switch (walletTransaction.getTransactionType()) {
        case TOPUP_WALLET -> {
          description = "Nap tien vao vi";
          typeStr = "TOPUP";
        }
        case TOPUP_DEPOSIT -> {
          description = "Nap tien ky quy";
          typeStr = "TOPUP";
        }
        case BOOKING_EARNING -> {
          description = "Thu nhap tu don booking";
          typeStr = "BOOKING_EARNING";
        }
        case FEE_DEDUCTION -> {
          description = "Chiet khau hoa hong don hang";
          typeStr = "COMMISSION";
        }
        case PAYOUT -> {
          description = "Rut tien ve ngan hang";
          typeStr = "WITHDRAW";
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
      .description(description)
      .build();
  }
}
