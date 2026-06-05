package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.admin.CreateDoctorAccountRequest;
import com.minduc.happabi.dto.response.admin.DoctorAccountResponse;
import com.minduc.happabi.service.admin.IAdminDoctorAccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/doctors")
@RequiredArgsConstructor
@Tag(name = "Admin Doctors", description = "Doctor account management")
@SecurityRequirement(name = "bearerAuth")
public class AdminDoctorController {

    private final IAdminDoctorAccountService adminDoctorAccountService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DOCTOR:CREATE')")
    public ResponseEntity<BaseResponse<DoctorAccountResponse>> createDoctorAccount(
            @Valid @RequestBody CreateDoctorAccountRequest request) {
        DoctorAccountResponse response = adminDoctorAccountService.createDoctorAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(response));
    }
}
