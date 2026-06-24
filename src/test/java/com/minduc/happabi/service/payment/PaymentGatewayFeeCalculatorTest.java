package com.minduc.happabi.service.payment;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayFeeCalculatorTest {

    @Test
    void calculatesRoundedGatewayFeeAndNetReceivedAmount() {
        ISystemConfigService configService = mock(ISystemConfigService.class);
        when(configService.getConfigValue("PAYOS_GATEWAY_FEE_RATE", "0"))
                .thenReturn("0.0055");
        PaymentGatewayFeeCalculator calculator = new PaymentGatewayFeeCalculator(configService);

        PaymentGatewayFeeCalculator.GatewayFeeQuote quote = calculator.quote(390000);

        assertThat(quote.rate()).isEqualByComparingTo("0.0055");
        assertThat(quote.feeAmount()).isEqualTo(2145L);
        assertThat(quote.netReceivedAmount()).isEqualTo(387855L);
    }

    @Test
    void keepsFullAmountWhenGatewayFeeIsNotConfigured() {
        ISystemConfigService configService = mock(ISystemConfigService.class);
        when(configService.getConfigValue("PAYOS_GATEWAY_FEE_RATE", "0"))
                .thenReturn("0");
        PaymentGatewayFeeCalculator calculator = new PaymentGatewayFeeCalculator(configService);

        PaymentGatewayFeeCalculator.GatewayFeeQuote quote = calculator.quote(135000);

        assertThat(quote.feeAmount()).isZero();
        assertThat(quote.netReceivedAmount()).isEqualTo(135000L);
    }

    @Test
    void rejectsInvalidFeeRate() {
        assertThatThrownBy(() -> PaymentGatewayFeeCalculator.parseFeeRate("1.01"))
                .isInstanceOf(AppException.class);
    }
}
