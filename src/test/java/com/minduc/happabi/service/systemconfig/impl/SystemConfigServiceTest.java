package com.minduc.happabi.service.systemconfig.impl;

import com.minduc.happabi.dto.request.admin.UpdateFinancialConfigurationRequest;
import com.minduc.happabi.dto.response.admin.FinancialConfigurationResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.service.booking.PlatformCommissionCalculator;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.payment.PaymentGatewayFeeCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private INotificationPublisher notificationPublisher;

    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigService(
                systemConfigRepository,
                nurseProfileRepository,
                notificationPublisher);
    }

    @Test
    void updateFinancialConfigurationNotifiesActiveNursesWhenCommissionRateChanges() {
        Map<String, SystemConfig> configs = configStore("0.15", "0.0055");
        stubConfigStore(configs);
        when(nurseProfileRepository.findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.ACTIVE))
                .thenReturn(List.of(activeNurse("22222222-2222-2222-2222-222222222222"),
                        activeNurse("33333333-3333-3333-3333-333333333333")));

        FinancialConfigurationResponse response = systemConfigService.updateFinancialConfiguration(
                financialRequest("0.006", "0.20"),
                ADMIN_ID.toString());

        assertThat(response.getPayOsGatewayFeeRate()).isEqualByComparingTo("0.006");
        assertThat(response.getPlatformCommissionRate()).isEqualByComparingTo("0.20");
        verify(notificationPublisher, times(2)).publish(
                any(UUID.class),
                eq(UserRole.NURSE),
                eq(NotificationType.PLATFORM_COMMISSION_UPDATED),
                eq("Cập nhật tỷ lệ chia sẻ doanh thu"),
                contains("Happabi giữ 20%, điều dưỡng nhận 80%"),
                eq("SYSTEM_CONFIG"),
                eq(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY));
    }

    @Test
    void updateFinancialConfigurationDoesNotNotifyWhenCommissionRateIsUnchanged() {
        Map<String, SystemConfig> configs = configStore("0.15", "0.0055");
        stubConfigStore(configs);

        FinancialConfigurationResponse response = systemConfigService.updateFinancialConfiguration(
                financialRequest("0.006", "0.1500"),
                ADMIN_ID.toString());

        assertThat(response.getPlatformCommissionRate()).isEqualByComparingTo("0.15");
        verify(nurseProfileRepository, never()).findByNurseStatusOrderByUpdatedAtAsc(any());
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void updateConfigNotifiesActiveNursesWhenPlatformCommissionRateChanges() {
        Map<String, SystemConfig> configs = configStore("0.12", "0.0055");
        stubConfigStore(configs);
        when(nurseProfileRepository.findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.ACTIVE))
                .thenReturn(List.of(activeNurse("44444444-4444-4444-4444-444444444444")));

        systemConfigService.updateConfig(
                PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY,
                "0.18",
                ADMIN_ID.toString());

        assertThat(configs.get(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY).getConfigValue())
                .isEqualTo("0.18");
        verify(notificationPublisher).publish(
                eq(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                eq(UserRole.NURSE),
                eq(NotificationType.PLATFORM_COMMISSION_UPDATED),
                eq("Cập nhật tỷ lệ chia sẻ doanh thu"),
                contains("Happabi giữ 18%, điều dưỡng nhận 82%"),
                eq("SYSTEM_CONFIG"),
                eq(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY));
    }

    private UpdateFinancialConfigurationRequest financialRequest(String payOsRate, String commissionRate) {
        UpdateFinancialConfigurationRequest request = new UpdateFinancialConfigurationRequest();
        request.setPayOsGatewayFeeRate(new BigDecimal(payOsRate));
        request.setPlatformCommissionRate(new BigDecimal(commissionRate));
        return request;
    }

    private Map<String, SystemConfig> configStore(String platformCommissionRate, String payOsGatewayFeeRate) {
        Map<String, SystemConfig> configs = new HashMap<>();
        configs.put(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY,
                SystemConfig.builder()
                        .configKey(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY)
                        .configValue(platformCommissionRate)
                        .build());
        configs.put(PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY,
                SystemConfig.builder()
                        .configKey(PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY)
                        .configValue(payOsGatewayFeeRate)
                        .build());
        return configs;
    }

    private void stubConfigStore(Map<String, SystemConfig> configs) {
        when(systemConfigRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(configs.get(invocation.getArgument(0))));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(configs.get(invocation.getArgument(0))));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> {
                    SystemConfig saved = invocation.getArgument(0);
                    configs.put(saved.getConfigKey(), saved);
                    return saved;
                });
    }

    private NurseProfile activeNurse(String userId) {
        User user = User.builder()
                .id(UUID.fromString(userId))
                .fullName("Active Nurse")
                .build();
        return NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .nurseStatus(NurseStatus.ACTIVE)
                .build();
    }
}
