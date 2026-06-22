package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.repository.NurseWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NurseWalletProvisioningService {

    private final NurseWalletRepository nurseWalletRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public NurseWallet ensureWallet(UUID nurseProfileId) {
        return nurseWalletRepository.findByNurseId(nurseProfileId)
                .orElseGet(() -> nurseWalletRepository.save(NurseWallet.builder()
                        .nurseId(nurseProfileId)
                        .balance(BigDecimal.ZERO)
                        .depositBalance(BigDecimal.ZERO)
                        .lockedWithdrawalAmount(BigDecimal.ZERO)
                        .build()));
    }
}
