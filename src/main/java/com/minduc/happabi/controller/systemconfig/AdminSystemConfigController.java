package com.minduc.happabi.controller.systemconfig;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.entity.SystemConfig;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import com.minduc.happabi.config.security.UserContext;
import com.minduc.happabi.common.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/system-configs")
@Tag(name = "Admin System Config", description = "System configuration management for administrators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemConfigController {

    private final ISystemConfigService systemConfigService;

    @GetMapping
    @Operation(summary = "Get all system configurations")
    public ResponseEntity<BaseResponse<List<SystemConfig>>> getAllConfigs() {
        return ResponseEntity.ok(BaseResponse.ok("Get all configs success", systemConfigService.getAllConfigs()));
    }

    @PostMapping("/{key}")
    @Operation(summary = "Update a system configuration")
    public ResponseEntity<BaseResponse<Void>> updateConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        String adminId = AuthUtils.getUserContext()
                .map(UserContext::userId)
                .map(java.util.UUID::toString)
                .orElse("SYSTEM");

        systemConfigService.updateConfig(key, value, adminId);
        return ResponseEntity.ok(BaseResponse.ok("Update config success", null));
    }
}
