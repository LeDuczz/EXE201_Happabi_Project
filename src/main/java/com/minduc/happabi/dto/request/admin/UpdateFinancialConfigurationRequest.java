package com.minduc.happabi.dto.request.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateFinancialConfigurationRequest {

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal payOsGatewayFeeRate;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal platformCommissionRate;
}
