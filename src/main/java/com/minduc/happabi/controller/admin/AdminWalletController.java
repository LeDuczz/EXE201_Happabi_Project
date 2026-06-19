package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.admin.AdminWalletResponse;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallet")
@Tag(name = "Admin Wallet", description = "Platform wallet balance and transaction ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final IAdminWalletLedgerService adminWalletLedgerService;

    @GetMapping
    @Operation(summary = "Get platform wallet balance and transaction history")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> getPlatformWallet(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get admin wallet successfully",
                adminWalletLedgerService.getPlatformWallet(pageable)));
    }
}
