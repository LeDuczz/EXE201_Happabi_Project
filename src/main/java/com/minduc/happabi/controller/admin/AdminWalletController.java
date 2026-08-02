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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/admin/wallet")
@Tag(name = "Admin Wallet", description = "Platform wallet balance and transaction ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final IAdminWalletLedgerService adminWalletLedgerService;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @GetMapping
    @Operation(summary = "Get platform wallet balance and transaction history")
    public ResponseEntity<BaseResponse<AdminWalletResponse>> getPlatformWallet(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(BaseResponse.ok("Get admin wallet successfully",
                adminWalletLedgerService.getPlatformWallet(
                        pageable,
                        transactionType,
                        direction,
                        startOfDay(fromDate),
                        exclusiveEndOfDay(toDate))));
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private Instant exclusiveEndOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
