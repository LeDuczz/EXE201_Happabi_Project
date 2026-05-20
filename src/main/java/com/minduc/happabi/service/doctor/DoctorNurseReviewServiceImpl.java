package com.minduc.happabi.service.doctor;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.*;
import com.minduc.happabi.enums.*;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.*;
import com.minduc.happabi.service.notification.NurseNotificationService;
import com.minduc.happabi.service.nurse.KycSensitiveDocumentCleanupService;
import com.minduc.happabi.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorNurseReviewServiceImpl implements DoctorNurseReviewService {

    private static final Duration SENSITIVE_DOCUMENT_TTL = Duration.ofMinutes(15);
    private static final String CURRENT_CONTRACT_VERSION = "NURSE_MVP_2026_05";

    private final UserRepository userRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseContractRepository contractRepository;
    private final NurseReviewEventRepository reviewEventRepository;
    private final S3Service s3Service;
    private final NurseNotificationService nurseNotificationService;
    private final KycSensitiveDocumentCleanupService kycSensitiveDocumentCleanupService;
    private final NurseOnboardingMapper nurseOnboardingMapper;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @TimedAction("get_pending_nurse_reviews")
    public List<NurseOnboardingResponse> getPendingReviews() {
        return nurseProfileRepository.findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.PENDING_REVIEW).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @TimedAction("get_nurse_review_detail")
    public NurseOnboardingResponse getForDoctor(UUID profileId) {
        return toResponse(findProfile(profileId));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @LogExecution
    @TimedAction("get_nurse_kyc_document_url")
    @AuditAction(action = "VIEW_NURSE_KYC_DOCUMENT", resourceType = "NURSE_PROFILE")
    public String getKycDocumentUrl(UUID profileId, String side) {
        NurseProfile profile = findProfile(profileId);
        NurseKyc kyc = nurseKycRepository.findByNurse(profile)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Nurse KYC not found."));
        String key = "back".equalsIgnoreCase(side) ? kyc.getCccdBackS3Key() : kyc.getCccdFrontS3Key();
        return s3Service.presign(key, SENSITIVE_DOCUMENT_TTL);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @LogExecution
    @TimedAction("get_nurse_certification_document_url")
    @AuditAction(action = "VIEW_NURSE_CERTIFICATION_DOCUMENT", resourceType = "NURSE_CERTIFICATION")
    public String getCertificationDocumentUrl(UUID certificationId) {
        NurseCertification certification = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Certification not found."));
        return s3Service.presign(certification.getDocumentS3Key(), SENSITIVE_DOCUMENT_TTL);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @LogExecution
    @TimedAction("approve_nurse_profile")
    @AuditAction(action = "APPROVE_NURSE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse approve(UUID profileId, ReviewNurseProfileRequest request) {
        NurseProfile profile = findProfile(profileId);
        if (profile.getNurseStatus() != NurseStatus.PENDING_REVIEW) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Only pending review profiles can be approved.");
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

        transition(profile, NurseStatus.APPROVED_PENDING_CONTRACT, NurseReviewAction.APPROVED, actor, request.getNote());
        profile.setRejectionReason(null);
        NurseProfile saved = nurseProfileRepository.save(profile);
        ensurePendingContract(saved);
        nurseNotificationService.notifyApprovedPendingContract(saved);

        return toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    @TimedAction("reject_nurse_profile")
    @AuditAction(action = "REJECT_NURSE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse reject(UUID profileId, ReviewNurseProfileRequest request) {
        NurseProfile profile = findProfile(profileId);
        if (profile.getNurseStatus() != NurseStatus.PENDING_REVIEW) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Only pending review profiles can be rejected.");
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
        return toResponse(saved);
    }

    private NurseProfile findProfile(UUID profileId) {
        return nurseProfileRepository.findById(profileId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile not found."));
    }

    private User currentUser() {
        String sub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        return userRepository.findByCognitoSub(sub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private void transition(NurseProfile profile, NurseStatus toStatus, NurseReviewAction action, User actor, String note) {
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
        List<NurseCertification> certifications = certificationRepository.findByNurseOrderByIdDesc(profile);
        NurseContract latestContract = contractRepository.findTopByNurseOrderByCreatedAtDesc(profile).orElse(null);
        return nurseOnboardingMapper.toResponse(profile, kyc, certifications, latestContract);
    }
}
