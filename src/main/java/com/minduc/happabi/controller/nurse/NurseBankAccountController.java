package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.UpsertNurseBankAccountRequest;
import com.minduc.happabi.dto.response.nurse.NurseBankAccountResponse;
import com.minduc.happabi.service.nurse.INurseBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nurses/me/bank-account")
@RequiredArgsConstructor
@Tag(name = "Nurse Bank Account", description = "Nurse payout bank account APIs")
@PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
public class NurseBankAccountController {

    private final INurseBankAccountService nurseBankAccountService;

    @GetMapping
    @Operation(summary = "Get my payout bank account")
    public ResponseEntity<BaseResponse<NurseBankAccountResponse>> getMine() {
        return ResponseEntity.ok(BaseResponse.ok("Get bank account successfully.",
                nurseBankAccountService.getMyBankAccount()));
    }

    @PutMapping
    @Operation(summary = "Create or update my payout bank account")
    public ResponseEntity<BaseResponse<NurseBankAccountResponse>> upsert(
            @Valid @RequestBody UpsertNurseBankAccountRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Bank account saved successfully.",
                nurseBankAccountService.upsertMyBankAccount(request)));
    }
}
