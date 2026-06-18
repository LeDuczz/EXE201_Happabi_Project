package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.CreateNurseAvailabilityWindowRequest;
import com.minduc.happabi.dto.response.nurse.NurseAvailabilityWindowResponse;
import com.minduc.happabi.service.nurse.INurseAvailabilityWindowService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nurses/me/availability-windows")
@RequiredArgsConstructor
@Tag(name = "Nurse Availability", description = "Nurse availability windows for booking")
@SecurityRequirement(name = "bearerAuth")
public class NurseAvailabilityWindowController {

    private final INurseAvailabilityWindowService availabilityWindowService;

    @GetMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<List<NurseAvailabilityWindowResponse>>> getMyWindows() {
        return ResponseEntity.ok(BaseResponse.ok(availabilityWindowService.getMyWindows()));
    }

    @PostMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<NurseAvailabilityWindowResponse>> createMyWindow(
            @Valid @RequestBody CreateNurseAvailabilityWindowRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Availability window created.",
                availabilityWindowService.createMyWindow(request)));
    }

    @DeleteMapping("/{windowId}")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<NurseAvailabilityWindowResponse>> cancelMyWindow(
            @PathVariable UUID windowId) {
        return ResponseEntity.ok(BaseResponse.ok("Availability window cancelled.",
                availabilityWindowService.cancelMyWindow(windowId)));
    }
}
