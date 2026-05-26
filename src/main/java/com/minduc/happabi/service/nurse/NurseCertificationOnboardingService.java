package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.nurse.CreateNurseCertificationRequest;
import com.minduc.happabi.dto.response.nurse.NurseCertificationResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.service.doctor.DoctorNurseReviewCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseCertificationOnboardingService {

    private final NurseCertificationRepository certificationRepository;
    private final IS3Service s3Service;
    private final NurseOnboardingMapper nurseOnboardingMapper;
    private final NurseOnboardingSupportService supportService;
    private final DoctorNurseReviewCacheService reviewCacheService;

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_ADD_CERTIFICATION")
    @AuditAction(action = "NURSE_ADD_CERTIFICATION", resourceType = "NURSE_CERTIFICATION")
    public NurseCertificationResponse addMyCertification(CreateNurseCertificationRequest request, MultipartFile document) {
        NurseProfile profile = supportService.currentNurseProfile();
        supportService.ensureEditable(profile);
        if (document == null || document.isEmpty()) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Certification document is required.");
        }

        NurseCertification certification = NurseCertification.builder()
                .nurse(profile)
                .certName(request.getCertName())
                .issuedBy(request.getIssuedBy())
                .issuedDate(request.getIssuedDate())
                .expiryDate(request.getExpiryDate())
                .documentS3Key(s3Service.upload("certifications", profile.getUser().getId().toString(), document))
                .isVerified(false)
                .build();
        NurseCertification saved = certificationRepository.save(certification);
        reviewCacheService.evictReviewCaches(profile.getId());
        return nurseOnboardingMapper.toCertificationResponse(saved);
    }
}
