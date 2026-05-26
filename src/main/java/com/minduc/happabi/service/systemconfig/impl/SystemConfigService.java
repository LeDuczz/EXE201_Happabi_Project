package com.minduc.happabi.service.systemconfig.impl;

import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
    @CacheEvict(value = "app_config", key = "#key")
    public void updateConfig(String key, String newValue, String adminId) {
        SystemConfig config = systemConfigRepository.findById(key).orElse(new SystemConfig());
        config.setConfigKey(key);
        config.setConfigValue(newValue);
        config.setUpdatedBy(UUID.fromString(adminId));
        systemConfigRepository.save(config);
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

}
