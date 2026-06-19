package com.minduc.happabi.controller.wallet;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.WalletDTO;
import com.minduc.happabi.service.nurse.INurseWalletService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "APIs for managing nurse wallets and transactions")
public class WalletController {
    private final INurseWalletService nurseWalletService;


//    @PreAuthorize("hasRole('ROLE_NURSE') AND hasAuthority('VIEW_WALLET')")
    @GetMapping("/me")
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public ResponseEntity<BaseResponse<WalletDTO>> getMyWallet() {
        WalletDTO walletDTO = nurseWalletService.getMyWalletInfo();
        return ResponseEntity.ok(BaseResponse.ok(walletDTO));
    }








}
