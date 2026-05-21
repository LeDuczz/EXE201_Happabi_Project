package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.PlatformRevenueRepository;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.nurse.INurseWalletService;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import com.minduc.happabi.service.systemconfig.impl.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NurseWalletService implements INurseWalletService {

    private final NurseWalletRepository nurseWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformRevenueRepository platformRevenueRepository;
    private final ISystemConfigService systemConfigService;

    @Override
    public boolean canAcceptCashBooking(String nurseId, BigDecimal bookingAmount) {
        NurseWallet wallet = nurseWalletRepository.findById(UUID.fromString(nurseId))
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));

        BigDecimal feeRate = systemConfigService.getPlatformFeeRate();
        BigDecimal requiredFee = bookingAmount.multiply(feeRate);

        BigDecimal totalAvailable = wallet.getBalance().add(wallet.getDepositBalance());
        return totalAvailable.compareTo(requiredFee) >= 0;
    }
}
