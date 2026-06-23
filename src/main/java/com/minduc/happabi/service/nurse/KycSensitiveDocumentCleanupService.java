package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseKycRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycSensitiveDocumentCleanupService {

    private static final Duration APPROVED_CCCD_IMAGE_RETENTION = Duration.ofDays(15);

    private final NurseKycRepository nurseKycRepository;
    private final NurseKycCleanupProcessor cleanupProcessor;

    @Value("${app.kyc.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    public void scheduleApprovedCccdImageCleanup(NurseKyc kyc) {
        if (kyc.getCccdImagesDeletedAt() != null || !hasCccdImage(kyc)) {
            return;
        }

        OffsetDateTime deleteAfter = OffsetDateTime.now().plus(APPROVED_CCCD_IMAGE_RETENTION);
        kyc.setCccdImagesDeleteAfter(deleteAfter);
        log.info("[KYC] Scheduled CCCD image cleanup: kycId={} deleteAfter={}", kyc.getId(), deleteAfter);
    }

    @Scheduled(fixedDelayString = "${app.kyc.cleanup.fixed-delay-ms:3600000}")
    @LogExecution
    @TimedAction("KYC_CLEANUP_DUE_APPROVED_CCCD_IMAGES")
    public void cleanupDueApprovedCccdImages() {
        if (!cleanupEnabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        nurseKycRepository
                .findTop50ByCccdImagesDeletedAtIsNullAndCccdImagesDeleteAfterLessThanEqual(now)
                .forEach(kyc -> {
                    try {
                        cleanupProcessor.cleanupCccdImages(kyc.getId());
                    } catch (Exception e) {
                        log.error("Cleanup failed", e);
                    }
                });
    }

    private boolean hasCccdImage(NurseKyc kyc) {
        return (kyc.getCccdFrontS3Key() != null && !kyc.getCccdFrontS3Key().isBlank())
                || (kyc.getCccdBackS3Key() != null && !kyc.getCccdBackS3Key().isBlank());
    }
}
