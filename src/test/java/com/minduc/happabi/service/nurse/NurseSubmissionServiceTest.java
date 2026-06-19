package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.doctor.DoctorNurseReviewCacheService;
import com.minduc.happabi.service.user.UserCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseSubmissionServiceTest {

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private NurseOnboardingSupportService supportService;

    @Mock
    private DoctorNurseReviewCacheService reviewCacheService;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private NurseSubmissionService service;

    @Test
    void submitMyProfileValidatesTransitionsSavesAndEvictsCaches() {
        User nurseUser = User.builder()
                .id(UUID.randomUUID())
                .cognitoSub("sub-123")
                .build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        NurseProfile profile = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(nurseUser)
                .nurseStatus(NurseStatus.PENDING_SUBMIT)
                .build();
        NurseOnboardingResponse expected = NurseOnboardingResponse.builder().profileId(profile.getId()).build();
        when(supportService.currentNurseProfile()).thenReturn(profile);
        when(supportService.currentUser()).thenReturn(actor);
        when(nurseProfileRepository.save(profile)).thenReturn(profile);
        when(supportService.toResponse(profile)).thenReturn(expected);

        NurseOnboardingResponse response = service.submitMyProfile();

        assertThat(response).isEqualTo(expected);
        InOrder inOrder = inOrder(supportService, nurseProfileRepository);
        inOrder.verify(supportService).ensureEditable(profile);
        inOrder.verify(supportService).validateReadyToSubmit(profile);
        inOrder.verify(supportService).transition(
                profile,
                NurseStatus.PENDING_REVIEW,
                NurseReviewAction.SUBMITTED,
                actor,
                "Submitted for doctor review");
        inOrder.verify(nurseProfileRepository).save(profile);
        verify(reviewCacheService).evictReviewCaches(profile.getId());
        verify(userCacheService).evictProfiles("sub-123");
    }
}
