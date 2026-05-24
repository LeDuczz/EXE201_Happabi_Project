package com.minduc.happabi.dto.request.nurse;

import com.minduc.happabi.enums.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopUpRequest {
    @NotNull(message = "Amount cannot blank")
    @Min(value = 10000, message = "Min is 10,000 VNĐ")
    private BigDecimal amount;

    @NotNull(message = "Transaction type cannot blank")
    private TransactionType topUpType; //TOPUP_WALLET / TOPUP_DEPOSIT

}
