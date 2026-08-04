package com.minduc.happabi.service.systemconfig.impl;

import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.dto.request.admin.UpdateFinancialConfigurationRequest;
import com.minduc.happabi.dto.response.admin.FinancialConfigurationResponse;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.booking.PlatformCommissionCalculator;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import com.minduc.happabi.service.payment.PaymentGatewayFeeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService implements ISystemConfigService {

    private static final String SYSTEM_CONFIG_RESOURCE = "SYSTEM_CONFIG";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SystemConfigRepository systemConfigRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final INotificationPublisher notificationPublisher;

    @LogExecution
    @Cacheable(value = "app_config", key = "#key")
    @Override
    public String getConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key).map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    @LogExecution
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN:MANAGE')")
    @CacheEvict(value = "app_config", key = "#key")
    public void updateConfig(String key, String newValue, String adminId) {
        BigDecimal previousCommissionRate = null;
        BigDecimal requestedCommissionRate = null;
        if (PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY.equals(key)) {
            PaymentGatewayFeeCalculator.parseFeeRate(newValue);
        }
        if (PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY.equals(key)) {
            previousCommissionRate = currentPlatformCommissionRate();
            requestedCommissionRate = PlatformCommissionCalculator.parseCommissionRate(newValue);
        }
        SystemConfig config = systemConfigRepository.findById(key).orElse(new SystemConfig());
        config.setConfigKey(key);
        config.setConfigValue(newValue);
        config.setUpdatedBy(UUID.fromString(adminId));
        systemConfigRepository.save(config);

        notifyActiveNursesIfCommissionChanged(previousCommissionRate, requestedCommissionRate);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN:MANAGE')")
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN:MANAGE')")
    public FinancialConfigurationResponse getFinancialConfiguration() {
        return FinancialConfigurationResponse.builder()
                .payOsGatewayFeeRate(PaymentGatewayFeeCalculator.parseFeeRate(getConfigValue(
                        PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY,
                        PaymentGatewayFeeCalculator.DEFAULT_PAYOS_GATEWAY_FEE_RATE)))
                .platformCommissionRate(PlatformCommissionCalculator.parseCommissionRate(getConfigValue(
                        PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY,
                        PlatformCommissionCalculator.DEFAULT_PLATFORM_COMMISSION_RATE)))
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN:MANAGE')")
    @LogExecution
    @TimedAction("UPDATE_FINANCIAL_CONFIGURATION")
    @AuditAction(action = "UPDATE_FINANCIAL_CONFIGURATION", resourceType = "SYSTEM_CONFIG")
    @org.springframework.cache.annotation.CacheEvict(value = "app_config", allEntries = true)
    public FinancialConfigurationResponse updateFinancialConfiguration(UpdateFinancialConfigurationRequest request,
                                                                        String adminId) {
        PaymentGatewayFeeCalculator.parseFeeRate(request.getPayOsGatewayFeeRate().toPlainString());
        BigDecimal previousCommissionRate = currentPlatformCommissionRate();
        BigDecimal requestedCommissionRate = PlatformCommissionCalculator.parseCommissionRate(
                request.getPlatformCommissionRate().toPlainString());
        UUID updatedBy = UUID.fromString(adminId);

        saveFinancialConfig(PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY,
                request.getPayOsGatewayFeeRate().stripTrailingZeros().toPlainString(),
                "Configured PayOS fee rate. Decimal value, for example 0.0055 for 0.55%.", updatedBy);
        saveFinancialConfig(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY,
                request.getPlatformCommissionRate().stripTrailingZeros().toPlainString(),
                "Platform commission rate retained from each booking. Decimal value, for example 0.15 for 15%.", updatedBy);

        notifyActiveNursesIfCommissionChanged(previousCommissionRate, requestedCommissionRate);

        return getFinancialConfiguration();
    }

    private void saveFinancialConfig(String key, String value, String description, UUID updatedBy) {
        SystemConfig config = systemConfigRepository.findById(key).orElseGet(SystemConfig::new);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        config.setUpdatedBy(updatedBy);
        systemConfigRepository.save(config);
    }

    private BigDecimal currentPlatformCommissionRate() {
        return systemConfigRepository.findByConfigKey(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY)
                .map(SystemConfig::getConfigValue)
                .map(PlatformCommissionCalculator::parseCommissionRate)
                .orElseGet(() -> PlatformCommissionCalculator.parseCommissionRate(
                        PlatformCommissionCalculator.DEFAULT_PLATFORM_COMMISSION_RATE));
    }

    private void notifyActiveNursesIfCommissionChanged(BigDecimal previousRate, BigDecimal newRate) {
        if (previousRate == null || newRate == null || previousRate.compareTo(newRate) == 0) {
            return;
        }

        String platformPercent = formatPercent(newRate);
        String nursePercent = formatPercent(BigDecimal.ONE.subtract(newRate));
        String message = "Tỷ lệ chia sẻ doanh thu đã được cập nhật. Happabi giữ "
                + platformPercent + ", điều dưỡng nhận " + nursePercent
                + " cho các booking mới.";

        nurseProfileRepository.findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.ACTIVE).forEach(profile -> {
            if (profile.getUser() == null) {
                return;
            }
            try {
                notificationPublisher.publish(
                        profile.getUser().getId(),
                        NotificationType.PLATFORM_COMMISSION_UPDATED,
                        "Cập nhật tỷ lệ chia sẻ doanh thu",
                        message,
                        SYSTEM_CONFIG_RESOURCE,
                        PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY);
            } catch (RuntimeException ex) {
                log.warn("[SystemConfig] Failed to publish platform commission update notification: nurseProfileId={}",
                        profile.getId(), ex);
            }
        });
    }

    private String formatPercent(BigDecimal rate) {
        return rate.multiply(ONE_HUNDRED).stripTrailingZeros().toPlainString() + "%";
    }

}
