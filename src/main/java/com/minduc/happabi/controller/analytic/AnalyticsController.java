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
        return ResponseEntity.ok(BaseResponse.ok("Analytic gmv last 30 days successfully!", response));
    }

    @AuditAction(action = "READ", resourceType = "ADMIN")
    @PreAuthorize("hasAuthority('ADMIN:ANALYTICS') and hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<?>> getSummary() {
        return ResponseEntity.ok(
                BaseResponse.ok("Get dashboard summary successfully!", realtimeMetricsService.getDashboardSummary()));
    }

    @AuditAction(action = "READ", resourceType = "ADMIN")
    @PreAuthorize("hasAuthority('ADMIN:ANALYTICS') and hasRole('ADMIN')")
    @GetMapping("/role-distribution")
    public ResponseEntity<BaseResponse<?>> getRoleDistribution() {
        return ResponseEntity.ok(
                BaseResponse.ok("Get role distribution successfully!", realtimeMetricsService.getRoleDistribution()));
    }

    @AuditAction(action = "READ", resourceType = "ADMIN")
    @PreAuthorize("hasAuthority('ADMIN:ANALYTICS') and hasRole('ADMIN')")
    @GetMapping("/bookings/daily")
    public ResponseEntity<BaseResponse<?>> getDailyBookings() {
        return ResponseEntity.ok(BaseResponse.ok("Get daily bookings successfully!",
                realtimeMetricsService.getDailyBookingCountLast30Days()));
    }

    @AuditAction(action = "READ", resourceType = "ADMIN")
    @PreAuthorize("hasAuthority('ADMIN:ANALYTICS') and hasRole('ADMIN')")
    @GetMapping("/user-growth")
    public ResponseEntity<BaseResponse<?>> getUserGrowth() {
        return ResponseEntity
                .ok(BaseResponse.ok("Get user growth successfully!", realtimeMetricsService.getUserGrowthLast30Days()));
    }

}
