package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.nurse.UpdateNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.doctor.DoctorNurseReviewCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseProfileOnboardingService {

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseOnboardingSupportService supportService;
    private final DoctorNurseReviewCacheService reviewCacheService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_GET_ONBOARDING")
    public NurseOnboardingResponse getMyOnboarding() {
        return supportService.toResponse(supportService.currentNurseProfile());
    }

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_UPDATE_PROFILE")
    @AuditAction(action = "NURSE_UPDATE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse updateMyProfile(UpdateNurseProfileRequest request) {
        NurseProfile profile = supportService.currentNurseProfile();
        supportService.ensureEditable(profile);

        if (request.getLicenseNumber() != null) profile.setLicenseNumber(request.getLicenseNumber());
        if (request.getDateOfBirth() != null) profile.setDateOfBirth(request.getDateOfBirth());
        if (request.getSpecialty() != null) profile.setSpecialty(request.getSpecialty());
        if (request.getExperienceYears() != null) profile.setExperienceYears(request.getExperienceYears());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getServiceArea() != null) profile.setServiceArea(request.getServiceArea());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getCity() != null) profile.setCity(request.getCity());

        NurseProfile saved = nurseProfileRepository.save(profile);
        reviewCacheService.evictReviewCaches(saved.getId());
        return supportService.toResponse(saved);
    }
}
