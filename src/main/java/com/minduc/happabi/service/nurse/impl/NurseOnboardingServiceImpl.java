package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.dto.request.nurse.*;
import com.minduc.happabi.dto.response.nurse.*;
import com.minduc.happabi.entity.*;
import com.minduc.happabi.enums.*;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.*;
import com.minduc.happabi.service.notification.NurseNotificationService;
import com.minduc.happabi.service.nurse.NurseOnboardingService;
import com.minduc.happabi.service.s3.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NurseOnboardingServiceImpl implements NurseOnboardingService {

    private static final String CURRENT_CONTRACT_VERSION = "NURSE_MVP_2026_05";

    private final UserRepository userRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final NurseKycRepository nurseKycRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseContractRepository contractRepository;
    private final NurseReviewEventRepository reviewEventRepository;
    private final S3Service s3Service;
    private final NurseNotificationService nurseNotificationService;
    private final NurseOnboardingMapper nurseOnboardingMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_get_onboarding")
    public NurseOnboardingResponse getMyOnboarding() {
        return toResponse(currentNurseProfile());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_update_profile")
    @AuditAction(action = "NURSE_UPDATE_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse updateMyProfile(UpdateNurseProfileRequest request) {
        NurseProfile profile = currentNurseProfile();
        ensureEditable(profile);

        if (request.getLicenseNumber() != null) profile.setLicenseNumber(request.getLicenseNumber());
        if (request.getDateOfBirth() != null) profile.setDateOfBirth(request.getDateOfBirth());
        if (request.getSpecialty() != null) profile.setSpecialty(request.getSpecialty());
        if (request.getExperienceYears() != null) profile.setExperienceYears(request.getExperienceYears());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getServiceArea() != null) profile.setServiceArea(request.getServiceArea());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getCity() != null) profile.setCity(request.getCity());

        return toResponse(nurseProfileRepository.save(profile));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_update_kyc")
    @AuditAction(action = "NURSE_UPDATE_KYC", resourceType = "NURSE_KYC")
    public NurseOnboardingResponse updateMyKyc(UpdateNurseKycRequest request, MultipartFile frontImage, MultipartFile backImage) {
        NurseProfile profile = currentNurseProfile();
        ensureEditable(profile);

        NurseKyc kyc = nurseKycRepository.findByNurse(profile)
                .orElseGet(() -> NurseKyc.builder().nurse(profile).build());
        kyc.setCccdNumber(request.getCccdNumber());
        kyc.setCccdName(request.getCccdName());
        kyc.setCccdDob(request.getCccdDob());
        kyc.setCccdAddress(request.getCccdAddress());
        kyc.setEkycStatus(EkycStatus.PENDING);

        String ownerId = profile.getUser().getId().toString();
        handleKycImage(frontImage, ownerId, kyc::getCccdFrontS3Key, kyc::setCccdFrontS3Key);

        handleKycImage(backImage, ownerId, kyc::getCccdBackS3Key, kyc::setCccdBackS3Key);

        nurseKycRepository.save(kyc);
        return toResponse(profile);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_add_certification")
    @AuditAction(action = "NURSE_ADD_CERTIFICATION", resourceType = "NURSE_CERTIFICATION")
    public NurseCertificationResponse addMyCertification(CreateNurseCertificationRequest request, MultipartFile document) {
        NurseProfile profile = currentNurseProfile();
        ensureEditable(profile);
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
        return nurseOnboardingMapper.toCertificationResponse(certificationRepository.save(certification));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_submit_profile")
    @AuditAction(action = "NURSE_SUBMIT_PROFILE", resourceType = "NURSE_PROFILE")
    public NurseOnboardingResponse submitMyProfile() {
        NurseProfile profile = currentNurseProfile();
        ensureEditable(profile);
        validateReadyToSubmit(profile);
        transition(profile, NurseStatus.PENDING_REVIEW, NurseReviewAction.SUBMITTED, currentUser(), "Submitted for doctor review");
        return toResponse(nurseProfileRepository.save(profile));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_sign_contract")
    @AuditAction(action = "NURSE_SIGN_CONTRACT", resourceType = "NURSE_CONTRACT")
    public NurseOnboardingResponse signContract(SignNurseContractRequest request, HttpServletRequest httpRequest) {
        NurseProfile profile = currentNurseProfile();
        if (profile.getNurseStatus() != NurseStatus.APPROVED_PENDING_CONTRACT) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile is not ready for contract signing.");
        }

        NurseContract contract = contractRepository.findTopByNurseAndStatusOrderByCreatedAtDesc(profile, NurseContractStatus.PENDING)
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

        transition(profile, NurseStatus.ACTIVE, NurseReviewAction.CONTRACT_SIGNED, currentUser(), "Contract signed");
        nurseProfileRepository.save(profile);
        nurseNotificationService.notifyActive(profile);
        return toResponse(profile);
    }

    private User currentUser() {
        String sub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        return userRepository.findByCognitoSub(sub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private NurseProfile currentNurseProfile() {
        User user = currentUser();
        return nurseProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile not found."));
    }

    private void ensureEditable(NurseProfile profile) {
        NurseStatus status = profile.getNurseStatus();
        if (status != NurseStatus.PENDING_SUBMIT && status != NurseStatus.REJECTED) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse profile is locked for review.");
        }
    }

    private void validateReadyToSubmit(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        if (!nurseOnboardingMapper.isProfileCompleted(profile)
                || !nurseOnboardingMapper.isKycCompleted(kyc)
                || !isCertificationsCompleted(profile)) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Nurse onboarding is incomplete.");
        }
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

    private NurseOnboardingResponse toResponse(NurseProfile profile) {
        NurseKyc kyc = nurseKycRepository.findByNurse(profile).orElse(null);
        List<NurseCertification> certifications = certificationRepository.findByNurseOrderByIdDesc(profile);
        NurseContract latestContract = contractRepository.findTopByNurseOrderByCreatedAtDesc(profile).orElse(null);
        return nurseOnboardingMapper.toResponse(profile, kyc, certifications, latestContract);
    }

    private boolean isCertificationsCompleted(NurseProfile profile) {
        return certificationRepository.countByNurse(profile) > 0;
    }

    private void handleKycImage(MultipartFile image, String ownerId, Supplier<String> oldKeySupplier,
                                Consumer<String> newKeySetter) {
        if (image == null || image.isEmpty()) {
            return;
        }

        String oldKey = oldKeySupplier.get();

        String newKey = s3Service.upload("kyc", ownerId, image);

        newKeySetter.accept(newKey);

        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            eventPublisher.publishEvent(
                    new S3ObjectDeleteRequestedEvent(oldKey, "KYC_REPLACED")
            );
        }
    }

}
