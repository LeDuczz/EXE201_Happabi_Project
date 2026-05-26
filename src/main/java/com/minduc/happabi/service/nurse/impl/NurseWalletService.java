package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.TransactionDTO;
import com.minduc.happabi.dto.WalletDTO;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.exception.code.WalletTransactionErrorCode;
import com.minduc.happabi.mapper.WalletTransactionMapper;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.PlatformRevenueRepository;
import com.minduc.happabi.repository.SystemConfigRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.nurse.INurseWalletService;
import com.minduc.happabi.service.systemconfig.ISystemConfigService;
import com.minduc.happabi.service.systemconfig.impl.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NurseWalletService implements INurseWalletService {

    private final NurseWalletRepository nurseWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformRevenueRepository platformRevenueRepository;
    private final ISystemConfigService systemConfigService;
    private final WalletTransactionMapper walletTransactionMapper;

  @Override
  public WalletDTO getMyWalletInfo(String nurseId) {
    NurseWallet wallet = nurseWalletRepository.findByNurseId(UUID.fromString(nurseId))
      .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));

    List<WalletTransaction> transactions = walletTransactionRepository
      .findTop20ByNurseIdOrderByCreatedAtDesc(UUID.fromString(nurseId))
      .orElseThrow(() -> new AppException(WalletTransactionErrorCode.NURSE_ID_NOT_FOUND));

    List<TransactionDTO> transactionDTOS = transactions.stream()
      .map(walletTransactionMapper::toTransactionDTO).toList();


    return WalletDTO.builder()
      .balance(wallet.getBalance())
      .pledgeAmount(wallet.getDepositBalance())
      .transactions(transactionDTOS)
      .build();
  }

  @Override
    public boolean canAcceptCashBooking(String nurseId, BigDecimal bookingAmount) {
        NurseWallet wallet = nurseWalletRepository.findByNurseId(UUID.fromString(nurseId))
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));
        BigDecimal feeRate, requiredFee, totalAvailable;

        try {
             feeRate = new BigDecimal(systemConfigService.getConfigValue("PLATFORM_FEE_RATE", "0.10"));
             requiredFee = bookingAmount.multiply(feeRate);
             totalAvailable = wallet.getBalance().add(wallet.getDepositBalance());
        } catch (Exception e) {
            throw new AppException(NurseWalletErrorCode.CASH_BOOKING_ACCEPTANCE_ERROR, e.getMessage());
        }
        return totalAvailable.compareTo(requiredFee) >= 0;
    }
}
