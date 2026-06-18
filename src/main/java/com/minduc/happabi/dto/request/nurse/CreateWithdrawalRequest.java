package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateWithdrawalRequest {

    @NotNull
    @DecimalMin(value = "1000.00", message = "Withdrawal amount must be at least 1000 VND")
    private BigDecimal amount;
}
