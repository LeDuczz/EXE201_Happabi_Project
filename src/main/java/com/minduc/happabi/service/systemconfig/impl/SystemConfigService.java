package com.minduc.happabi.service.systemconfig.impl;

import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.dto.request.admin.UpdateFinancialConfigurationRequest;
import com.minduc.happabi.dto.response.admin.FinancialConfigurationResponse;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.service.booking.PlatformCommissionCalculator;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import com.minduc.happabi.service.payment.PaymentGatewayFeeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemConfigService implements ISystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

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
        if (PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY.equals(key)) {
            PaymentGatewayFeeCalculator.parseFeeRate(newValue);
        }
        if (PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY.equals(key)) {
            PlatformCommissionCalculator.parseCommissionRate(newValue);
        }
        SystemConfig config = systemConfigRepository.findById(key).orElse(new SystemConfig());
        config.setConfigKey(key);
        config.setConfigValue(newValue);
        config.setUpdatedBy(UUID.fromString(adminId));
        systemConfigRepository.save(config);
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
        PlatformCommissionCalculator.parseCommissionRate(request.getPlatformCommissionRate().toPlainString());
        UUID updatedBy = UUID.fromString(adminId);

        saveFinancialConfig(PaymentGatewayFeeCalculator.PAYOS_GATEWAY_FEE_RATE_KEY,
                request.getPayOsGatewayFeeRate().stripTrailingZeros().toPlainString(),
                "Configured PayOS fee rate. Decimal value, for example 0.0055 for 0.55%.", updatedBy);
        saveFinancialConfig(PlatformCommissionCalculator.PLATFORM_COMMISSION_RATE_KEY,
                request.getPlatformCommissionRate().stripTrailingZeros().toPlainString(),
                "Platform commission rate retained from each booking. Decimal value, for example 0.15 for 15%.", updatedBy);

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

}
