package com.minduc.happabi.dto.response.admin;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FinancialConfigurationResponse {

    private BigDecimal payOsGatewayFeeRate;
    private BigDecimal platformCommissionRate;
}
