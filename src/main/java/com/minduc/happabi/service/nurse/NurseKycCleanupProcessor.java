package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.KycErrorCode;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseKycCleanupProcessor {

    private final NurseKycRepository nurseKycRepository;
    private final IS3Service s3Service;

    @Transactional
    public void cleanupCccdImages(UUID kycId) {

        NurseKyc kyc = nurseKycRepository.findById(kycId)
                .orElseThrow(() -> new AppException((KycErrorCode.KYC_DOCUMENT_NOT_FOUND)
                        , "NurseKyc not found: " + kycId));

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
}
