package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.request.nurse.UpsertNurseBankAccountRequest;
import com.minduc.happabi.dto.response.nurse.NurseBankAccountResponse;
import com.minduc.happabi.entity.NurseBankAccount;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseBankAccountStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseBankAccountRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.nurse.INurseBankAccountService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NurseBankAccountServiceImpl implements INurseBankAccountService {

    private final NurseBankAccountRepository nurseBankAccountRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserAccountLookupService userAccountLookupService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public NurseBankAccountResponse getMyBankAccount() {
        NurseProfile nurseProfile = currentNurseProfile();
        return nurseBankAccountRepository.findByNurseProfile_Id(nurseProfile.getId())
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @LogExecution
    @TimedAction("UPSERT_NURSE_BANK_ACCOUNT")
    @AuditAction(action = "UPSERT_NURSE_BANK_ACCOUNT", resourceType = "NURSE_BANK_ACCOUNT")
    @Transactional
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public NurseBankAccountResponse upsertMyBankAccount(UpsertNurseBankAccountRequest request) {
        NurseProfile nurseProfile = currentNurseProfile();
        NurseBankAccount bankAccount = nurseBankAccountRepository.findByNurseProfile_Id(nurseProfile.getId())
                .orElseGet(() -> NurseBankAccount.builder()
                        .nurseProfile(nurseProfile)
                        .build());
        bankAccount.setBankName(cleanRequired(request.getBankName()));
        bankAccount.setBankAccountNumber(cleanRequired(request.getBankAccountNumber()));
        bankAccount.setBankAccountHolder(cleanRequired(request.getBankAccountHolder()));
        bankAccount.setStatus(NurseBankAccountStatus.ACTIVE);
        return toResponse(nurseBankAccountRepository.save(bankAccount));
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private NurseBankAccountResponse toResponse(NurseBankAccount bankAccount) {
        return NurseBankAccountResponse.builder()
                .id(bankAccount.getId())
                .nurseProfileId(bankAccount.getNurseProfile().getId())
                .bankName(bankAccount.getBankName())
                .bankAccountNumber(bankAccount.getBankAccountNumber())
                .bankAccountHolder(bankAccount.getBankAccountHolder())
                .status(bankAccount.getStatus())
                .createdAt(bankAccount.getCreatedAt())
                .updatedAt(bankAccount.getUpdatedAt())
                .build();
    }

    private String cleanRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_BANK_ACCOUNT_REQUIRED);
        }
        return value.trim();
    }
}
