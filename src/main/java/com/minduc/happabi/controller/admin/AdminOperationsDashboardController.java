package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse;
import com.minduc.happabi.service.admin.IAdminOperationsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Operational dashboard for platform administrators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsDashboardController {

    private final IAdminOperationsDashboardService adminOperationsDashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Get platform operations overview")
    public ResponseEntity<BaseResponse<AdminOperationsDashboardResponse>> getOverview() {
        return ResponseEntity.ok(BaseResponse.ok("Get admin operations dashboard successfully.",
                adminOperationsDashboardService.getOverview()));
    }
}
