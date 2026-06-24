package com.minduc.happabi.service.booking;

import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformCommissionCalculatorTest {

    @Test
    void calculatesPlatformCommissionAndNurseEarningFromCurrentRate() {
        ISystemConfigService configService = mock(ISystemConfigService.class);
        when(configService.getConfigValue("PLATFORM_COMMISSION_RATE", "0.15"))
                .thenReturn("0.15");
        PlatformCommissionCalculator calculator = new PlatformCommissionCalculator(configService);

        PlatformCommissionCalculator.CommissionQuote quote = calculator.quote(450000);

        assertThat(quote.platformFeeAmount()).isEqualTo(67500L);
        assertThat(quote.nurseEarningAmount()).isEqualTo(382500L);
    }
}
