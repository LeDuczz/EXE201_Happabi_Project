package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.admin.ApproveWithdrawalRequest;
import com.minduc.happabi.dto.request.admin.RejectWithdrawalRequest;
import com.minduc.happabi.dto.response.nurse.NurseWithdrawalResponse;
import com.minduc.happabi.enums.NurseWithdrawalStatus;
import com.minduc.happabi.service.nurse.INurseWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/withdrawal-requests")
@RequiredArgsConstructor
@Tag(name = "Admin Nurse Withdrawals", description = "Admin manual payout workflow APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNurseWithdrawalController {

    private final INurseWithdrawalService nurseWithdrawalService;

    @GetMapping
    @Operation(summary = "Get nurse withdrawal requests")
    public ResponseEntity<BaseResponse<Page<NurseWithdrawalResponse>>> getRequests(
            @RequestParam(required = false) NurseWithdrawalStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get withdrawal requests successfully.",
                nurseWithdrawalService.getWithdrawalRequests(status, pageable)));
    }

    @PostMapping(value = "/{requestId}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Approve nurse withdrawal request after manual bank transfer")
    public ResponseEntity<BaseResponse<NurseWithdrawalResponse>> approve(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute ApproveWithdrawalRequest request,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return ResponseEntity.ok(BaseResponse.ok("Withdrawal request approved.",
                nurseWithdrawalService.approveWithdrawalRequest(requestId, request, evidence)));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject nurse withdrawal request and release held amount")
    public ResponseEntity<BaseResponse<NurseWithdrawalResponse>> reject(
            @PathVariable UUID requestId,
            @Valid @org.springframework.web.bind.annotation.RequestBody RejectWithdrawalRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Withdrawal request rejected.",
                nurseWithdrawalService.rejectWithdrawalRequest(requestId, request)));
    }
}
