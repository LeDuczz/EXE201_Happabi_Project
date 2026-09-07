package com.minduc.happabi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class WalletDTO {
  private BigDecimal balance;
  private BigDecimal pledgeAmount;
  private BigDecimal lockedWithdrawalAmount;
  private List<TransactionDTO> transactions;
}
