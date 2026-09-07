package com.minduc.happabi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TransactionDTO {
  private String id;
  private BigDecimal amount;
  private String type;
  private String status;
  private String createdAt;
  private String description;
}
