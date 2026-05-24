package com.minduc.happabi.service.nurse;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseReviewEvent;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseReviewEventRepository;
import com.minduc.happabi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseOnboardingSupportService {

    private final UserRepository userRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseContractRepository contractRepository;
    private final NurseReviewEventRepository reviewEventRepository;
    private final NurseOnboardingMapper nurseOnboardingMapper;

    public User currentUser() {
        String sub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        return userRepository.findByCognitoSub(sub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    public NurseProfile currentNurseProfile() {
        User user = currentUser();
        return nurseProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile not found."));
    }

    public void ensureEditable(NurseProfile profile) {
        NurseStatus status = profile.getNurseStatus();
        if (status != NurseStatus.PENDING_SUBMIT && status != NurseStatus.REJECTED) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile is locked for review.");
        }
    }

    public void validateReadyToSubmit(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        if (!nurseOnboardingMapper.isProfileCompleted(profile)
                || !nurseOnboardingMapper.isKycCompleted(kyc)
                || !isCertificationsCompleted(profile)) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse onboarding is incomplete.");
        }
    }

    public void transition(NurseProfile profile, NurseStatus toStatus,
                           NurseReviewAction action, User actor, String note) {
        NurseStatus fromStatus = profile.getNurseStatus();
        profile.setNurseStatus(toStatus);
        profile.setLastStatusChangedAt(OffsetDateTime.now());
        profile.setStatusChangedBy(actor);
        reviewEventRepository.save(NurseReviewEvent.builder()
                .nurse(profile)
                .action(action)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actor(actor)
                .note(note)
                .build());
    }

    public NurseOnboardingResponse toResponse(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        List<NurseCertification> certifications = certificationRepository.findByNurseOrderByIdDesc(profile);
        NurseContract latestContract = contractRepository.findTopByNurseOrderByCreatedAtDesc(profile).orElse(null);
        return nurseOnboardingMapper.toResponse(profile, kyc, certifications, latestContract);
    }

    private boolean isCertificationsCompleted(NurseProfile profile) {
        return certificationRepository.countByNurse(profile) > 0;
    }
}
