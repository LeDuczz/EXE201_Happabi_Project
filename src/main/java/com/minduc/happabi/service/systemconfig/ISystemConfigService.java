package com.minduc.happabi.service.systemconfig;

import com.minduc.happabi.entity.SystemConfig;

import java.util.List;

public interface ISystemConfigService {

    String getConfigValue(String key, String defaultValue);

    void updateConfig(String key, String newValue, String adminId);

    List<SystemConfig> getAllConfigs();

}
