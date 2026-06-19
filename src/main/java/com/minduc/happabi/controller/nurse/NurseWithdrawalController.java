package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.CreateWithdrawalRequest;
import com.minduc.happabi.dto.response.nurse.NurseWithdrawalResponse;
import com.minduc.happabi.service.nurse.INurseWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nurses/me/withdrawal-requests")
@RequiredArgsConstructor
@Tag(name = "Nurse Withdrawals", description = "Nurse manual withdrawal request APIs")
@PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
public class NurseWithdrawalController {

    private static final int RECENT_WITHDRAWAL_REQUEST_LIMIT = 5;

    private final INurseWithdrawalService nurseWithdrawalService;

    @PostMapping
    @Operation(summary = "Create my withdrawal request")
    public ResponseEntity<BaseResponse<NurseWithdrawalResponse>> create(
            @Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.ok(BaseResponse.created("Withdrawal request created.",
                nurseWithdrawalService.createMyWithdrawalRequest(request)));
    }

    @GetMapping
    @Operation(summary = "Get my latest withdrawal requests")
    public ResponseEntity<BaseResponse<Page<NurseWithdrawalResponse>>> getMine() {
        Pageable pageable = PageRequest.of(
                0,
                RECENT_WITHDRAWAL_REQUEST_LIMIT,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(BaseResponse.ok("Get withdrawal requests successfully.",
                nurseWithdrawalService.getMyWithdrawalRequests(pageable)));
    }

    @DeleteMapping("/{requestId}")
    @Operation(summary = "Cancel my pending withdrawal request")
    public ResponseEntity<BaseResponse<NurseWithdrawalResponse>> cancel(@PathVariable UUID requestId) {
        return ResponseEntity.ok(BaseResponse.ok("Withdrawal request cancelled.",
                nurseWithdrawalService.cancelMyWithdrawalRequest(requestId)));
    }
}
