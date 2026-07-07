package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.KycErrorCode;
import com.minduc.happabi.repository.NurseKycRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseKycCleanupProcessor {

    private final NurseKycRepository nurseKycRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void cleanupCccdImages(UUID kycId) {
        NurseKyc kyc = nurseKycRepository.findById(kycId)
                .orElseThrow(() -> new AppException(
                        KycErrorCode.KYC_DOCUMENT_NOT_FOUND,
                        "NurseKyc not found: " + kycId));

        String frontKey = kyc.getCccdFrontS3Key();
        String backKey = kyc.getCccdBackS3Key();

        kyc.setCccdFrontS3Key(null);
        kyc.setCccdBackS3Key(null);
        kyc.setCccdImagesDeletedAt(OffsetDateTime.now());

        nurseKycRepository.save(kyc);
        publishDeleteAfterCommit(frontKey, kyc.getId() + ":CCCD_FRONT_RETENTION_EXPIRED");
        publishDeleteAfterCommit(backKey, kyc.getId() + ":CCCD_BACK_RETENTION_EXPIRED");
        log.info("[KYC] Scheduled retained CCCD image cleanup and cleared S3 keys: kycId={}", kyc.getId());
    }

    private void publishDeleteAfterCommit(String key, String reason) {
        if (key == null || key.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(key, reason));
        log.info("[KYC] Published retained CCCD image cleanup event: key={} reason={}", key, reason);
    }
}
