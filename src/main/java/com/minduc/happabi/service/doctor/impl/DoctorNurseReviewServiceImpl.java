package com.minduc.happabi.service.doctor.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.dto.response.nurse.NurseReviewSummaryResponse;
import com.minduc.happabi.entity.*;
import com.minduc.happabi.enums.*;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.*;
import com.minduc.happabi.service.doctor.DoctorNurseReviewCacheService;
import com.minduc.happabi.service.doctor.IDoctorNurseReviewService;
import com.minduc.happabi.service.notification.INurseNotificationService;
import com.minduc.happabi.service.nurse.KycSensitiveDocumentCleanupService;
import com.minduc.happabi.service.nurse.NurseAccessCacheService;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.user.UserCacheService;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.integration.s3.S3ObjectDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorNurseReviewServiceImpl implements IDoctorNurseReviewService {

    private static final String CURRENT_CONTRACT_VERSION = "NURSE_MVP_2026_05";

    private final UserRepository userRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseContractRepository contractRepository;
    private final NurseReviewEventRepository reviewEventRepository;
    private final IS3Service s3Service;
    private final INurseNotificationService nurseNotificationService;
    private final KycSensitiveDocumentCleanupService kycSensitiveDocumentCleanupService;
    private final NurseOnboardingMapper nurseOnboardingMapper;
    private final DoctorNurseReviewCacheService reviewCacheService;
    private final NurseAccessCacheService nurseAccessCacheService;
    private final UserCacheService userCacheService;
    private final IServiceEligibilityService serviceEligibilityService;

    @Override
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:READ')")
    @LogExecution
    @Transactional(readOnly = true)
    @TimedAction("GET_PENDING_NURSE_REVIEWS")
    public List<NurseReviewSummaryResponse> getPendingReviews() {
        return reviewCacheService.getPendingReviews().orElseGet(() -> {
            List<NurseReviewSummaryResponse> response = nurseProfileRepository
                    .findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.PENDING_REVIEW).stream()
                    .map(this::toSummaryResponse)
                    .toList();
            reviewCacheService.putPendingReviews(response);
            return response;
        });
    }

    @Override
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:READ')")
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("GET_NURSE_PROFILE_FOR_REVIEW")
    public NurseOnboardingResponse getForDoctor(UUID profileId) {
        return reviewCacheService.getDetail(profileId).orElseGet(() -> {
            NurseOnboardingResponse response = toResponse(findProfile(profileId));
            reviewCacheService.putDetail(profileId, response);
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:READ')")
    @LogExecution
    @TimedAction("DOWNLOAD_NURSE_KYC_DOCUMENT")
    @AuditAction(action = "DOWNLOAD_NURSE_KYC_DOCUMENT", resourceType = "NURSE_PROFILE")
    public S3ObjectDownload getKycDocumentFile(UUID profileId, String side) {
        NurseProfile profile = findProfile(profileId);
        NurseKyc kyc = nurseKycRepository.findByNurse(profile)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Nurse KYC not found."));
        return s3Service.download(kycDocumentKey(kyc, side));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:READ')")
    @LogExecution
    @TimedAction("DOWNLOAD_NURSE_CERTIFICATION_DOCUMENT")
    @AuditAction(action = "DOWNLOAD_NURSE_CERTIFICATION_DOCUMENT", resourceType = "NURSE_CERTIFICATION")
    public S3ObjectDownload getCertificationDocumentFile(UUID certificationId) {
        NurseCertification certification = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED,
                        "Certification not found."));
        return s3Service.download(certification.getDocumentS3Key());
    }

    @Override
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:APPROVE')")
    @Transactional
    @LogExecution
    @TimedAction("APPROVE_NURSE_PROFILE")
    @AuditAction(action = "APPROVE_NURSE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse approve(UUID profileId, ReviewNurseProfileRequest request) {
        NurseProfile profile = findProfile(profileId);
        if (profile.getNurseStatus() != NurseStatus.PENDING_REVIEW) {
            throw new AppException(AuthErrorCode.AUTH_FAILED,
                    "Only pending review profiles can be approved.");
        }

        User actor = currentUser();
        nurseKycRepository.findByNurse(profile).ifPresent(kyc -> {
            kyc.setEkycStatus(EkycStatus.PASSED);
            kyc.setReviewedBy(actor);
            kyc.setReviewedAt(OffsetDateTime.now());
            kyc.setReviewNote(request.getNote());
            kycSensitiveDocumentCleanupService.scheduleApprovedCccdImageCleanup(kyc);
            nurseKycRepository.save(kyc);
        });
        certificationRepository.findByNurseOrderByIdDesc(profile).forEach(cert -> {
            cert.setIsVerified(true);
            cert.setVerifiedAt(OffsetDateTime.now());
            cert.setVerifiedBy(actor);
            certificationRepository.save(cert);
        });
        serviceEligibilityService.verifyDeclaredSkills(profile, actor);

        transition(profile, NurseStatus.APPROVED_PENDING_CONTRACT,
                NurseReviewAction.APPROVED, actor, request.getNote());
        profile.setRejectionReason(null);
        NurseProfile saved = nurseProfileRepository.save(profile);
        ensurePendingContract(saved);
        nurseNotificationService.notifyApprovedPendingContract(saved);
        reviewCacheService.evictReviewCaches(saved.getId());
        userCacheService.evictProfiles(saved.getUser().getCognitoSub());

        return toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('DOCTOR') and hasAuthority('NURSE:APPROVE')")
    @Transactional
    @LogExecution
    @TimedAction("REJECT_NURSE_PROFILE")
    @AuditAction(action = "REJECT_NURSE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse reject(UUID profileId, ReviewNurseProfileRequest request) {
        NurseProfile profile = findProfile(profileId);
        if (profile.getNurseStatus() != NurseStatus.PENDING_REVIEW) {
            throw new AppException(AuthErrorCode.AUTH_FAILED,
                    "Only pending review profiles can be rejected.");
        }

        User actor = currentUser();
        String note = request.getNote();
        profile.setRejectionReason(note);
        nurseKycRepository.findByNurse(profile).ifPresent(kyc -> {
            kyc.setEkycStatus(EkycStatus.REVIEW_NEEDED);
            kyc.setReviewedBy(actor);
            kyc.setReviewedAt(OffsetDateTime.now());
            kyc.setReviewNote(note);
            nurseKycRepository.save(kyc);
        });

        transition(profile, NurseStatus.REJECTED, NurseReviewAction.REJECTED, actor, note);
        NurseProfile saved = nurseProfileRepository.save(profile);
        nurseNotificationService.notifyRejected(saved, note);
        reviewCacheService.evictReviewCaches(saved.getId());
        userCacheService.evictProfiles(saved.getUser().getCognitoSub());
        return toResponse(saved);
    }

    private NurseProfile findProfile(UUID profileId) {
        return nurseProfileRepository.findById(profileId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED,
                        "Nurse profile not found."));
    }

    private User currentUser() {
        String sub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        return userRepository.findByCognitoSub(sub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private String kycDocumentKey(NurseKyc kyc, String side) {
        return "back".equalsIgnoreCase(side) ? kyc.getCccdBackS3Key() : kyc.getCccdFrontS3Key();
    }

    private NurseReviewSummaryResponse toSummaryResponse(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        long certificationCount = certificationRepository.countByNurse(profile);
        return NurseReviewSummaryResponse.builder()
                .profileId(profile.getId())
                .userId(profile.getUser().getId())
                .fullName(profile.getUser().getFullName())
                .phone(profile.getUser().getPhone())
                .email(profile.getUser().getEmail())
                .specialty(profile.getSpecialty())
                .experienceYears(profile.getExperienceYears())
                .city(profile.getCity())
                .nurseStatus(profile.getNurseStatus())
                .lastStatusChangedAt(profile.getLastStatusChangedAt())
                .profileCompleted(nurseOnboardingMapper.isProfileCompleted(profile))
                .kycCompleted(nurseOnboardingMapper.isKycCompleted(kyc))
                .certificationsCompleted(certificationCount > 0)
                .certificationCount(certificationCount)
                .build();
    }

    private void transition(NurseProfile profile, NurseStatus toStatus,
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
        nurseAccessCacheService.evict(profile.getUser().getId());
    }

    private void ensurePendingContract(NurseProfile profile) {
        contractRepository.findTopByNurseOrderByCreatedAtDesc(profile)
                .filter(contract -> contract.getStatus() == NurseContractStatus.PENDING)
                .orElseGet(() -> contractRepository.save(NurseContract.builder()
                        .nurse(profile)
                        .contractVersion(CURRENT_CONTRACT_VERSION)
                        .status(NurseContractStatus.PENDING)
                        .build()));
    }

    private NurseOnboardingResponse toResponse(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        List<NurseCertification> certifications = certificationRepository
                .findByNurseOrderByIdDesc(profile);
        NurseContract latestContract = contractRepository
                .findTopByNurseOrderByCreatedAtDesc(profile).orElse(null);
        return nurseOnboardingMapper.toResponse(
                profile,
                kyc,
                certifications,
                serviceEligibilityService.getNurseSkills(profile, false),
                latestContract);
    }
}
