package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseSubmissionService {

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseOnboardingSupportService supportService;

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_SUBMIT_PROFILE")
    @AuditAction(action = "NURSE_SUBMIT_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse submitMyProfile() {
        NurseProfile profile = supportService.currentNurseProfile();
        supportService.ensureEditable(profile);
        supportService.validateReadyToSubmit(profile);
        supportService.transition(profile, NurseStatus.PENDING_REVIEW,
                NurseReviewAction.SUBMITTED, supportService.currentUser(), "Submitted for doctor review");
        return supportService.toResponse(nurseProfileRepository.save(profile));
    }
}
