package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.service.notification.INurseNotificationService;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NurseDepositActivationService {

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseWalletRepository nurseWalletRepository;
    private final NurseOnboardingSupportService supportService;
    private final INurseNotificationService nurseNotificationService;
    private final UserCacheService userCacheService;

    @LogExecution
    @TimedAction("ACTIVATE_NURSE_AFTER_DEPOSIT")
    @AuditAction(action = "ACTIVATE_NURSE_AFTER_DEPOSIT", resourceType = "NURSE_DEPOSIT")
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean activateIfDepositRequirementMet(UUID nurseProfileId) {
        NurseProfile profile = nurseProfileRepository.findByIdForUpdate(nurseProfileId)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        if (profile.getNurseStatus() != NurseStatus.PENDING_DEPOSIT) {
            return profile.getNurseStatus() == NurseStatus.ACTIVE;
        }

        NurseWallet wallet = nurseWalletRepository.findByNurseIdForUpdate(nurseProfileId)
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));
        if (wallet.getDepositBalance().compareTo(NurseDepositPolicy.MINIMUM_DEPOSIT_AMOUNT) < 0) {
            return false;
        }

        supportService.transition(
                profile,
                NurseStatus.ACTIVE,
                NurseReviewAction.DEPOSIT_PAID,
                null,
                "Minimum nurse deposit paid"
        );
        nurseProfileRepository.save(profile);
        nurseNotificationService.notifyDepositConfirmed(profile);
        userCacheService.evictProfiles(profile.getUser().getCognitoSub());
        return true;
    }
}
