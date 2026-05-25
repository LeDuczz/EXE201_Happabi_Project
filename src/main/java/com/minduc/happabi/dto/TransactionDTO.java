package com.minduc.happabi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TransactionDTO {
  private String id;
  private BigDecimal amount;
  private String type;
  private String status;
  private String createdAt;
  private String description;
}
