package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.request.user.UpdateNurseProfileDisplayRequest;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.mapper.NurseProfileMapper;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseProfileService {

    private final NurseProfileRepository nurseProfileRepository;
    private final WorkSessionRepository workSessionRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseContractRepository contractRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final NurseProfileMapper nurseProfileMapper;
    private final IS3Service s3Service;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('NURSE:READ')")
    @LogExecution
    @TimedAction("GET_NURSE_PROFILE")
    public NurseProfileResponse getNurseProfile() {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        NurseProfileResponse cached = userCacheService.getNurseProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = userAccountLookupService.findBySub(cognitoSub);
        String avatarUrl = s3Service.presign(user.getAvatarS3Key());
        NurseProfileResponse response = nurseProfileRepository.findByUser(user)
                .map(profile -> toResponse(profile, avatarUrl))
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        userCacheService.putNurseProfile(cognitoSub, response);
        return response;
    }

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @LogExecution
    @TimedAction("UPDATE_NURSE_PROFILE_DISPLAY")
    public NurseProfileResponse updateDisplayProfile(UpdateNurseProfileDisplayRequest request) {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        User user = userAccountLookupService.findBySub(cognitoSub);
        NurseProfile profile = nurseProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));

        if (request.getBio() != null) {
            profile.setBio(normalizeText(request.getBio()));
        }
        if (request.getServiceArea() != null) {
            profile.setServiceArea(normalizeText(request.getServiceArea()));
        }
        NurseProfile saved = nurseProfileRepository.save(profile);
        userCacheService.evictProfiles(cognitoSub);

        return toResponse(saved, s3Service.presign(user.getAvatarS3Key()));
    }

    private NurseProfileResponse toResponse(NurseProfile profile, String avatarUrl) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        NurseContract latestContract = contractRepository.findTopByNurseOrderByCreatedAtDesc(profile).orElse(null);
        List<NurseCertification> certifications = certificationRepository.findByNurseOrderByIdDesc(profile);

        NurseProfileResponse response = nurseProfileMapper.toResponse(profile, kyc, certifications, latestContract, avatarUrl);
        response.setTotalCompletedJobs((int) workSessionRepository.countByStatusInAndNurseProfile_Id(
                Set.of(WorkSessionStatus.COMPLETED, WorkSessionStatus.AUTO_CONFIRMED), profile.getId()));
        return response;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
