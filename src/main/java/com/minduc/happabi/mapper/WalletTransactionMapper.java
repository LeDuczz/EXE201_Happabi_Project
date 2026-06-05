package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.TransactionDTO;
import com.minduc.happabi.entity.WalletTransaction;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.minduc.happabi.enums.TransactionType.*;

@Component
public class WalletTransactionMapper {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault());

  public TransactionDTO toTransactionDTO(WalletTransaction walletTransaction) {
    if (walletTransaction == null) {
      return null;
    }

    String description = "";
    String typeStr = "";

    if (walletTransaction.getTransactionType() != null) {
      switch (walletTransaction.getTransactionType()) {
        case TOPUP_WALLET -> {
          description = "Nạp tiền vào ví";
          typeStr = "TOPUP";
        }
        case TOPUP_DEPOSIT -> {
          description = "Nạp tiền ký quỹ";
          typeStr = "TOPUP";
        }
        case FEE_DEDUCTION -> {
          description = "Chiết khấu hoa hồng đơn hàng";
          typeStr = "COMMISSION";
        }
        case PAYOUT -> {
          description = "Rút tiền về ngân hàng";
          typeStr = "WITHDRAW";
        }
        default -> {
          description = "Giao dịch khác";
          typeStr = walletTransaction.getTransactionType().name();
        }
      }
    }

    return TransactionDTO.builder()
      .id(walletTransaction.getId())
      .amount(walletTransaction.getAmount())
      .type(typeStr)
      .status(walletTransaction.getStatus() != null ? walletTransaction.getStatus().name() : null)
      .createdAt(walletTransaction.getCreatedAt() != null ? DATE_TIME_FORMATTER.format(walletTransaction.getCreatedAt()) : null)
      .description(description)
      .build();

  }

}
