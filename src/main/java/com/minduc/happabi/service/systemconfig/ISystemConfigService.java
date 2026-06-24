package com.minduc.happabi.service.systemconfig;

import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.dto.request.admin.UpdateFinancialConfigurationRequest;
import com.minduc.happabi.dto.response.admin.FinancialConfigurationResponse;

import java.util.List;

public interface ISystemConfigService {

    String getConfigValue(String key, String defaultValue);

    void updateConfig(String key, String newValue, String adminId);

    List<SystemConfig> getAllConfigs();

    FinancialConfigurationResponse getFinancialConfiguration();

    FinancialConfigurationResponse updateFinancialConfiguration(UpdateFinancialConfigurationRequest request,
                                                                 String adminId);

}
