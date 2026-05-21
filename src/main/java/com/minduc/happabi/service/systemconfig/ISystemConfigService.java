package com.minduc.happabi.service.systemconfig;

import java.math.BigDecimal;

public interface ISystemConfigService {
    String getConfigValue(String key, String defaultValue);
    BigDecimal getPlatformFeeRate();
    void updateConfig(String key, String newValue, String adminId);
}
