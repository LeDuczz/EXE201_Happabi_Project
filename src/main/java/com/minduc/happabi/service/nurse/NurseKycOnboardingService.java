package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.dto.request.nurse.UpdateNurseKycRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.EkycStatus;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseKycOnboardingService {

    private final NurseKycRepository nurseKycRepository;
    private final IS3Service s3Service;
    private final NurseOnboardingSupportService supportService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("NURSE_UPDATE_KYC")
    @AuditAction(action = "NURSE_UPDATE_KYC", resourceType = "NURSE_KYC")
    public NurseOnboardingResponse updateMyKyc(UpdateNurseKycRequest request,
                                               MultipartFile frontImage,
                                               MultipartFile backImage) {
        NurseProfile profile = supportService.currentNurseProfile();
        supportService.ensureEditable(profile);

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
        return supportService.toResponse(profile);
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
            eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(oldKey, "KYC_REPLACED"));
        }
    }
}
