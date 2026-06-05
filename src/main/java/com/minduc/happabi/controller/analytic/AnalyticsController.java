package com.minduc.happabi.controller.analytic;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.service.auth.IRealtimeMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "APIs For Admin Dashboard Business Metrics")
public class AnalyticsController {

    private final IRealtimeMetricsService realtimeMetricsService;

    @AuditAction(action = "READ", resourceType = "ADMIN")
    @PreAuthorize("hasAuthority('ADMIN:ANALYTICS') and hasRole('ADMIN')")
    @GetMapping("/gmv/daily")
    public ResponseEntity<BaseResponse<?>> getDailyGmv() {
        Map<String, Double> response = realtimeMetricsService.getDailyGmvLast30Days();
        return ResponseEntity.ok(BaseResponse.ok("Analytic gmv last 30 days successfully!" ,response));
    }







}
