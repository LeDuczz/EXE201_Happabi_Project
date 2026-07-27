package com.minduc.happabi.service.doctor.impl;

import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseContractStatus;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseReviewEventRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.doctor.DoctorNurseReviewCacheService;
import com.minduc.happabi.service.notification.INurseNotificationService;
import com.minduc.happabi.service.nurse.KycSensitiveDocumentCleanupService;
import com.minduc.happabi.service.nurse.NurseAccessCacheService;
import com.minduc.happabi.service.user.UserCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorNurseReviewServiceImplTest {

    private final NurseContractRepository contractRepository = mock(NurseContractRepository.class);
    private final DoctorNurseReviewServiceImpl service = new DoctorNurseReviewServiceImpl(
            mock(UserRepository.class),
            mock(NurseProfileRepository.class),
            mock(NurseKycRepository.class),
            mock(NurseCertificationRepository.class),
            contractRepository,
            mock(NurseReviewEventRepository.class),
            mock(IS3Service.class),
            mock(INurseNotificationService.class),
            mock(KycSensitiveDocumentCleanupService.class),
            mock(NurseOnboardingMapper.class),
            mock(DoctorNurseReviewCacheService.class),
            mock(NurseAccessCacheService.class),
            mock(UserCacheService.class),
            mock(IServiceEligibilityService.class)
    );

    @Test
    void ensurePendingContractDoesNothingWhenLatestContractIsPending() {
        NurseProfile profile = NurseProfile.builder().build();
        when(contractRepository.findTopByNurseOrderByCreatedAtDesc(profile))
                .thenReturn(Optional.of(NurseContract.builder()
                        .nurse(profile)
                        .status(NurseContractStatus.PENDING)
                        .build()));

        ReflectionTestUtils.invokeMethod(service, "ensurePendingContract", profile);

        verify(contractRepository, never()).save(org.mockito.ArgumentMatchers.any(NurseContract.class));
    }

    @Test
    void ensurePendingContractCreatesPendingContractWhenLatestContractIsSigned() {
        NurseProfile profile = NurseProfile.builder().build();
        when(contractRepository.findTopByNurseOrderByCreatedAtDesc(profile))
                .thenReturn(Optional.of(NurseContract.builder()
                        .nurse(profile)
                        .status(NurseContractStatus.SIGNED)
                        .build()));

        ReflectionTestUtils.invokeMethod(service, "ensurePendingContract", profile);

        verify(contractRepository).save(argThat(contract ->
                contract.getNurse() == profile
                        && contract.getStatus() == NurseContractStatus.PENDING
                        && "NURSE_MVP_2026_05".equals(contract.getContractVersion())));
    }
}
