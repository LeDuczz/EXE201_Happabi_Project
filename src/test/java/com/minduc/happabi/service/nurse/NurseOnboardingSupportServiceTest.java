package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseReviewEvent;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.NurseOnboardingMapper;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseContractRepository;
import com.minduc.happabi.repository.NurseKycRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseReviewEventRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseOnboardingSupportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private NurseKycRepository nurseKycRepository;

    @Mock
    private NurseCertificationRepository certificationRepository;

    @Mock
    private NurseContractRepository contractRepository;

    @Mock
    private NurseReviewEventRepository reviewEventRepository;

    @Mock
    private NurseOnboardingMapper nurseOnboardingMapper;

    @Mock
    private NurseAccessCacheService nurseAccessCacheService;

    @Mock
    private IServiceEligibilityService serviceEligibilityService;

    @InjectMocks
    private NurseOnboardingSupportService service;

    @Test
    void ensureEditableAllowsPendingSubmitAndRejectedProfiles() {
        assertThatCode(() -> service.ensureEditable(NurseProfile.builder()
                .nurseStatus(NurseStatus.PENDING_SUBMIT)
                .build())).doesNotThrowAnyException();
        assertThatCode(() -> service.ensureEditable(NurseProfile.builder()
                .nurseStatus(NurseStatus.REJECTED)
                .build())).doesNotThrowAnyException();
    }

    @Test
    void ensureEditableRejectsLockedProfiles() {
        assertThatThrownBy(() -> service.ensureEditable(NurseProfile.builder()
                .nurseStatus(NurseStatus.PENDING_REVIEW)
                .build()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.AUTH_FAILED);
    }

    @Test
    void validateReadyToSubmitAllowsCompleteProfileKycAndCertification() {
        NurseProfile profile = NurseProfile.builder().id(UUID.randomUUID()).build();
        NurseKyc kyc = NurseKyc.builder().nurse(profile).build();
        when(nurseKycRepository.findByNurse(profile)).thenReturn(Optional.of(kyc));
        when(nurseOnboardingMapper.isProfileCompleted(profile)).thenReturn(true);
        when(nurseOnboardingMapper.isKycCompleted(kyc)).thenReturn(true);
        when(certificationRepository.countByNurse(profile)).thenReturn(1L);

        assertThatCode(() -> service.validateReadyToSubmit(profile)).doesNotThrowAnyException();
    }

    @Test
    void validateReadyToSubmitRejectsIncompleteOnboarding() {
        NurseProfile profile = NurseProfile.builder().id(UUID.randomUUID()).build();
        when(nurseKycRepository.findByNurse(profile)).thenReturn(Optional.empty());
        when(nurseOnboardingMapper.isProfileCompleted(profile)).thenReturn(true);

        assertThatThrownBy(() -> service.validateReadyToSubmit(profile))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.AUTH_FAILED);
    }

    @Test
    void transitionChangesStatusRecordsReviewEventAndEvictsAccessCache() {
        User nurseUser = User.builder().id(UUID.randomUUID()).build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        NurseProfile profile = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(nurseUser)
                .nurseStatus(NurseStatus.PENDING_SUBMIT)
                .build();

        service.transition(profile, NurseStatus.PENDING_REVIEW, NurseReviewAction.SUBMITTED, actor, "Submitted");

        assertThat(profile.getNurseStatus()).isEqualTo(NurseStatus.PENDING_REVIEW);
        assertThat(profile.getLastStatusChangedAt()).isNotNull();
        assertThat(profile.getStatusChangedBy()).isEqualTo(actor);
        ArgumentCaptor<NurseReviewEvent> captor = ArgumentCaptor.forClass(NurseReviewEvent.class);
        verify(reviewEventRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(NurseStatus.PENDING_SUBMIT);
        assertThat(captor.getValue().getToStatus()).isEqualTo(NurseStatus.PENDING_REVIEW);
        verify(nurseAccessCacheService).evict(nurseUser.getId());
    }

    @Test
    void toResponseLoadsRelatedDataAndDelegatesToMapper() {
        NurseProfile profile = NurseProfile.builder().id(UUID.randomUUID()).build();
        NurseKyc kyc = NurseKyc.builder().nurse(profile).build();
        NurseCertification cert = NurseCertification.builder().nurse(profile).certName("Cert").build();
        NurseOnboardingResponse response = NurseOnboardingResponse.builder().profileId(profile.getId()).build();
        when(nurseKycRepository.findByNurse(profile)).thenReturn(Optional.of(kyc));
        when(certificationRepository.findByNurseOrderByIdDesc(profile)).thenReturn(List.of(cert));
        when(contractRepository.findTopByNurseOrderByCreatedAtDesc(profile)).thenReturn(Optional.empty());
        when(serviceEligibilityService.getNurseSkills(profile, false)).thenReturn(List.of());
        when(nurseOnboardingMapper.toResponse(eq(profile), eq(kyc), eq(List.of(cert)), eq(List.of()), any()))
                .thenReturn(response);

        assertThat(service.toResponse(profile)).isEqualTo(response);
    }
}
