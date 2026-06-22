package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseReviewAction;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.service.notification.INurseNotificationService;
import com.minduc.happabi.service.user.UserCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseDepositActivationServiceTest {

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private NurseWalletRepository nurseWalletRepository;

    @Mock
    private NurseOnboardingSupportService supportService;

    @Mock
    private INurseNotificationService nurseNotificationService;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private NurseDepositActivationService service;

    @Test
    void activatesNurseWhenMinimumDepositIsPaid() {
        UUID nurseId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).cognitoSub("nurse-sub").build();
        NurseProfile profile = NurseProfile.builder()
                .id(nurseId)
                .user(user)
                .nurseStatus(NurseStatus.PENDING_DEPOSIT)
                .build();
        NurseWallet wallet = NurseWallet.builder()
                .nurseId(nurseId)
                .depositBalance(NurseDepositPolicy.MINIMUM_DEPOSIT_AMOUNT)
                .build();

        when(nurseProfileRepository.findByIdForUpdate(nurseId)).thenReturn(Optional.of(profile));
        when(nurseWalletRepository.findByNurseIdForUpdate(nurseId)).thenReturn(Optional.of(wallet));

        boolean activated = service.activateIfDepositRequirementMet(nurseId);

        assertThat(activated).isTrue();
        verify(supportService).transition(profile, NurseStatus.ACTIVE, NurseReviewAction.DEPOSIT_PAID,
                null, "Minimum nurse deposit paid");
        verify(nurseNotificationService).notifyDepositConfirmed(profile);
        verify(nurseProfileRepository).save(profile);
        verify(userCacheService).evictProfiles("nurse-sub");
    }

    @Test
    void keepsNursePendingWhenDepositIsBelowMinimum() {
        UUID nurseId = UUID.randomUUID();
        NurseProfile profile = NurseProfile.builder()
                .id(nurseId)
                .user(User.builder().id(UUID.randomUUID()).build())
                .nurseStatus(NurseStatus.PENDING_DEPOSIT)
                .build();
        NurseWallet wallet = NurseWallet.builder()
                .nurseId(nurseId)
                .depositBalance(new BigDecimal("299999"))
                .build();

        when(nurseProfileRepository.findByIdForUpdate(nurseId)).thenReturn(Optional.of(profile));
        when(nurseWalletRepository.findByNurseIdForUpdate(nurseId)).thenReturn(Optional.of(wallet));

        boolean activated = service.activateIfDepositRequirementMet(nurseId);

        assertThat(activated).isFalse();
        verify(supportService, never()).transition(any(), any(), any(), any(), any());
        verify(nurseNotificationService, never()).notifyDepositConfirmed(any());
    }
}
