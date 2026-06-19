package com.minduc.happabi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WalletDTO {
  private BigDecimal balance;
  private BigDecimal pledgeAmount;
  private BigDecimal lockedWithdrawalAmount;
  private List<TransactionDTO> transactions;
}
