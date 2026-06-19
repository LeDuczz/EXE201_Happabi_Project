package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.NurseAccessCacheService;
import com.minduc.happabi.service.user.UserCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NursePenaltyServiceImplTest {

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private INotificationPublisher notificationPublisher;

    @Mock
    private NurseAccessCacheService nurseAccessCacheService;

    @Mock
    private UserCacheService userCacheService;

    private NursePenaltyServiceImpl service;
    private UUID nurseProfileId;
    private UUID nurseUserId;
    private NurseProfile nurse;
    private WorkSession session;

    @BeforeEach
    void setUp() {
        service = new NursePenaltyServiceImpl(
                nurseProfileRepository,
                notificationPublisher,
                nurseAccessCacheService,
                userCacheService);

        nurseProfileId = UUID.randomUUID();
        nurseUserId = UUID.randomUUID();
        nurse = NurseProfile.builder()
                .id(nurseProfileId)
                .user(User.builder()
                        .id(nurseUserId)
                        .cognitoSub("nurse-cognito-sub")
                        .build())
                .nurseStatus(NurseStatus.ACTIVE)
                .availabilityStatus(AvailabilityStatus.AVAILABLE)
                .noShowViolationCount(0)
                .build();
        session = WorkSession.builder()
                .id(UUID.randomUUID())
                .nurseProfile(NurseProfile.builder().id(nurseProfileId).build())
                .scheduledStartAt(OffsetDateTime.now().minusHours(1))
                .build();
    }

    @Test
    void applyNoShowPenaltySuspendsBookingAvailabilityForFirstViolation() {
        when(nurseProfileRepository.findByIdForUpdate(nurseProfileId)).thenReturn(Optional.of(nurse));

        service.applyNoShowPenalty(session, " Nurse did not arrive ");

        assertThat(nurse.getNoShowViolationCount()).isEqualTo(1);
        assertThat(nurse.getNurseStatus()).isEqualTo(NurseStatus.ACTIVE);
        assertThat(nurse.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.OFFLINE);
        assertThat(nurse.getBookingSuspensionReason()).isEqualTo("Nurse did not arrive");
        assertThat(nurse.getBookingSuspendedUntil()).isAfter(OffsetDateTime.now().plusDays(2));
        assertThat(nurse.getBookingSuspendedUntil()).isBefore(OffsetDateTime.now().plusDays(4));
        assertThat(nurse.getPermanentlySuspendedAt()).isNull();
        verify(nurseProfileRepository).save(nurse);
        verify(nurseAccessCacheService).evict(nurseUserId);
        verify(userCacheService).evictProfiles("nurse-cognito-sub");
        verify(notificationPublisher).publish(
                eq(nurseUserId),
                eq(NotificationType.NURSE_SUSPENDED),
                eq("Booking availability suspended"),
                org.mockito.ArgumentMatchers.contains("Your booking availability has been suspended until"),
                eq("NURSE_PENALTY"),
                eq(nurseProfileId.toString()));
    }

    @Test
    void applyNoShowPenaltyUsesScheduledStartWhenFutureSessionIsPenalized() {
        OffsetDateTime futureStart = OffsetDateTime.now().plusDays(2).truncatedTo(ChronoUnit.SECONDS);
        session.setScheduledStartAt(futureStart);
        nurse.setNoShowViolationCount(1);
        when(nurseProfileRepository.findByIdForUpdate(nurseProfileId)).thenReturn(Optional.of(nurse));

        service.applyNoShowPenalty(session, null);

        assertThat(nurse.getNoShowViolationCount()).isEqualTo(2);
        assertThat(nurse.getBookingSuspendedUntil()).isEqualTo(futureStart.plusDays(7));
        assertThat(nurse.getBookingSuspensionReason()).isEqualTo("Nurse no-show confirmed by admin.");
    }

    @Test
    void applyNoShowPenaltyPermanentlySuspendsAtFourthViolation() {
        nurse.setNoShowViolationCount(3);
        nurse.setBookingSuspendedUntil(OffsetDateTime.now().plusDays(1));
        when(nurseProfileRepository.findByIdForUpdate(nurseProfileId)).thenReturn(Optional.of(nurse));

        service.applyNoShowPenalty(session, "Repeated no-show");

        assertThat(nurse.getNoShowViolationCount()).isEqualTo(4);
        assertThat(nurse.getNurseStatus()).isEqualTo(NurseStatus.SUSPENDED);
        assertThat(nurse.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.OFFLINE);
        assertThat(nurse.getBookingSuspendedUntil()).isNull();
        assertThat(nurse.getPermanentlySuspendedAt()).isNotNull();
        assertThat(nurse.getLastStatusChangedAt()).isNotNull();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationPublisher).publish(
                eq(nurseUserId),
                eq(NotificationType.NURSE_SUSPENDED),
                eq("Nurse account suspended"),
                messageCaptor.capture(),
                eq("NURSE_PENALTY"),
                eq(nurseProfileId.toString()));
        assertThat(messageCaptor.getValue()).contains("repeated no-show violations");
    }

    @Test
    void applyNoShowPenaltyRejectsMissingNurseProfile() {
        when(nurseProfileRepository.findByIdForUpdate(nurseProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyNoShowPenalty(session, "No-show"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.NURSE_PROFILE_NOT_FOUND);
    }
}
