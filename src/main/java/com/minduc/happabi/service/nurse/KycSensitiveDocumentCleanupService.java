package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycSensitiveDocumentCleanupService {

    private static final Duration APPROVED_CCCD_IMAGE_RETENTION = Duration.ofDays(15);

    private final NurseKycRepository nurseKycRepository;
    private final S3Service s3Service;

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
    @Transactional
    @LogExecution
    @TimedAction("KYC_CLEANUP_DUE_APPROVED_CCCD_IMAGES")
    @AuditAction(action = "KYC_CLEANUP_APPROVED_CCCD_IMAGES", resourceType = "NURSE_KYC")
    public void cleanupDueApprovedCccdImages() {
        if (!cleanupEnabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        nurseKycRepository.findTop50ByCccdImagesDeletedAtIsNullAndCccdImagesDeleteAfterLessThanEqual(now)
                .forEach(this::cleanupCccdImages);
    }

    private void cleanupCccdImages(NurseKyc kyc) {
        String frontKey = kyc.getCccdFrontS3Key();
        String backKey = kyc.getCccdBackS3Key();

        deleteIfPresent(frontKey, kyc.getId() + ":CCCD_FRONT_RETENTION_EXPIRED");
        deleteIfPresent(backKey, kyc.getId() + ":CCCD_BACK_RETENTION_EXPIRED");

        kyc.setCccdFrontS3Key(null);
        kyc.setCccdBackS3Key(null);
        kyc.setCccdImagesDeletedAt(OffsetDateTime.now());
        nurseKycRepository.save(kyc);
        log.info("[KYC] Deleted retained CCCD images and cleared S3 keys: kycId={}", kyc.getId());
    }

    private void deleteIfPresent(String key, String reason) {
        if (key == null || key.isBlank()) {
            return;
        }
        s3Service.delete(key);
        log.info("[KYC] Deleted retained CCCD image: key={} reason={}", key, reason);
    }

    private boolean hasCccdImage(NurseKyc kyc) {
        return (kyc.getCccdFrontS3Key() != null && !kyc.getCccdFrontS3Key().isBlank())
                || (kyc.getCccdBackS3Key() != null && !kyc.getCccdBackS3Key().isBlank());
    }
}
