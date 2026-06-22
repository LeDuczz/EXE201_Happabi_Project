package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.nurse.NurseDashboardResponse;
import com.minduc.happabi.service.nurse.INurseDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nurses/me/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('NURSE')")
public class NurseDashboardController {
    private final INurseDashboardService nurseDashboardService;

    @GetMapping
    public ResponseEntity<BaseResponse<NurseDashboardResponse>> getMyDashboard() {
        return ResponseEntity.ok(BaseResponse.ok("Get nurse dashboard successfully.", nurseDashboardService.getMyDashboard()));
    }
}
