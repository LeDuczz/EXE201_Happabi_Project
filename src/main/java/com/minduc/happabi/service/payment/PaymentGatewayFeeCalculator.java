package com.minduc.happabi.service.payment;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.CommonErrorCode;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class PaymentGatewayFeeCalculator {

    public static final String PAYOS_GATEWAY_FEE_RATE_KEY = "PAYOS_GATEWAY_FEE_RATE";
    public static final String DEFAULT_PAYOS_GATEWAY_FEE_RATE = "0";

    private final ISystemConfigService systemConfigService;

    public GatewayFeeQuote quote(long grossAmount) {
        if (grossAmount <= 0) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "Payment amount must be greater than zero.");
        }

        BigDecimal feeRate = parseFeeRate(systemConfigService.getConfigValue(
                PAYOS_GATEWAY_FEE_RATE_KEY,
                DEFAULT_PAYOS_GATEWAY_FEE_RATE
        ));
        long feeAmount = BigDecimal.valueOf(grossAmount)
                .multiply(feeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new GatewayFeeQuote(feeRate, feeAmount, grossAmount - feeAmount);
    }

    public static BigDecimal parseFeeRate(String rawValue) {
        try {
            BigDecimal feeRate = new BigDecimal(rawValue == null || rawValue.isBlank()
                    ? DEFAULT_PAYOS_GATEWAY_FEE_RATE
                    : rawValue.trim());
            if (feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) > 0) {
                throw new NumberFormatException("Fee rate must be between 0 and 1.");
            }
            return feeRate;
        } catch (NumberFormatException exception) {
            throw new AppException(CommonErrorCode.BAD_REQUEST,
                    "PAYOS_GATEWAY_FEE_RATE must be a decimal between 0 and 1.");
        }
    }

    public record GatewayFeeQuote(BigDecimal rate, long feeAmount, long netReceivedAmount) {
    }
}
