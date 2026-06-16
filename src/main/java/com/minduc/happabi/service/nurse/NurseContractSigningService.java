package com.minduc.happabi.service.nurse;

import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.dto.request.nurse.SignNurseContractRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseContractStatus;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.notification.INurseNotificationService;
import com.minduc.happabi.service.user.UserCacheService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseContractSigningService {

    private static final String CURRENT_CONTRACT_VERSION = "NURSE_MVP_2026_05";

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseContractRepository contractRepository;
    private final INurseNotificationService nurseNotificationService;
    private final NurseOnboardingSupportService supportService;
    private final UserCacheService userCacheService;

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_SIGN_CONTRACT")
    @AuditAction(action = "NURSE_SIGN_CONTRACT", resourceType = "NURSE_CONTRACT")
    public NurseOnboardingResponse signContract(SignNurseContractRequest request, HttpServletRequest httpRequest) {
        NurseProfile profile = supportService.currentNurseProfile();
        if (profile.getNurseStatus() != NurseStatus.APPROVED_PENDING_CONTRACT) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile is not ready for contract signing.");
        }

        NurseContract contract = contractRepository.findTopByNurseAndStatusOrderByCreatedAtDesc(
                        profile, NurseContractStatus.PENDING)
                .orElseGet(() -> NurseContract.builder()
                        .nurse(profile)
                        .contractVersion(CURRENT_CONTRACT_VERSION)
                        .status(NurseContractStatus.PENDING)
                        .build());
        contract.setSignedName(request.getSignedName());
        contract.setSignerIp(NetworkUtils.resolveClientIp(httpRequest));
        contract.setSignerUserAgent(httpRequest.getHeader("User-Agent"));
        contract.setSignedAt(OffsetDateTime.now());
        contract.setStatus(NurseContractStatus.SIGNED);
        contractRepository.save(contract);

        supportService.transition(profile, NurseStatus.ACTIVE,
                NurseReviewAction.CONTRACT_SIGNED, supportService.currentUser(), "Contract signed");
        nurseProfileRepository.save(profile);
        nurseNotificationService.notifyActive(profile);
        userCacheService.evictProfiles(profile.getUser().getCognitoSub());
        return supportService.toResponse(profile);
    }
}
