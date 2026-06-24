package com.minduc.happabi.service.booking;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.CommonErrorCode;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class PlatformCommissionCalculator {

    public static final String PLATFORM_COMMISSION_RATE_KEY = "PLATFORM_COMMISSION_RATE";
    public static final String DEFAULT_PLATFORM_COMMISSION_RATE = "0.15";

    private final ISystemConfigService systemConfigService;

    public CommissionQuote quote(long grossAmount) {
        if (grossAmount <= 0) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "Booking amount must be greater than zero.");
        }

        BigDecimal rate = parseCommissionRate(systemConfigService.getConfigValue(
                PLATFORM_COMMISSION_RATE_KEY,
                DEFAULT_PLATFORM_COMMISSION_RATE
        ));
        long platformFeeAmount = BigDecimal.valueOf(grossAmount)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new CommissionQuote(rate, platformFeeAmount, grossAmount - platformFeeAmount);
    }

    public static BigDecimal parseCommissionRate(String rawValue) {
        try {
            BigDecimal rate = new BigDecimal(rawValue == null || rawValue.isBlank()
                    ? DEFAULT_PLATFORM_COMMISSION_RATE
                    : rawValue.trim());
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new NumberFormatException("Commission rate must be between 0 and 1.");
            }
            return rate;
        } catch (NumberFormatException exception) {
            throw new AppException(CommonErrorCode.BAD_REQUEST,
                    "PLATFORM_COMMISSION_RATE must be a decimal between 0 and 1.");
        }
    }

    public record CommissionQuote(BigDecimal rate, long platformFeeAmount, long nurseEarningAmount) {
    }
}
