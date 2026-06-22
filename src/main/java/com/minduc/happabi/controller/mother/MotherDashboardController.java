package com.minduc.happabi.controller.mother;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.mother.MotherDashboardResponse;
import com.minduc.happabi.service.mother.IMotherDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mothers/me/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MOTHER')")
@Tag(name = "Mother Dashboard", description = "Live booking and nurse recommendation dashboard for mothers")
@SecurityRequirement(name = "bearerAuth")
public class MotherDashboardController {

    private final IMotherDashboardService motherDashboardService;

    @GetMapping
    @Operation(summary = "Get the current mother's dashboard")
    public ResponseEntity<BaseResponse<MotherDashboardResponse>> getMyDashboard() {
        return ResponseEntity.ok(BaseResponse.ok("Get mother dashboard successfully.",
                motherDashboardService.getMyDashboard()));
    }
}
