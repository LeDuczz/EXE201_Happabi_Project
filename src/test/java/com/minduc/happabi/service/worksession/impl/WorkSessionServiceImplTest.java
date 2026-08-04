package com.minduc.happabi.service.worksession.impl;

import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.mapper.WorkSessionMapper;
import com.minduc.happabi.observability.audit.AuditRecorder;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WorkSessionChecklistItemRepository;
import com.minduc.happabi.repository.WorkSessionEvidenceRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.booking.IBookingSettlementService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.NurseAvailabilityStatusSyncService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkSessionServiceImplTest {

    @Mock
    private WorkSessionRepository workSessionRepository;
    @Mock
    private WorkSessionChecklistItemRepository checklistItemRepository;
    @Mock
    private WorkSessionEvidenceRepository evidenceRepository;
    @Mock
    private NurseProfileRepository nurseProfileRepository;
    @Mock
    private UserAccountLookupService userAccountLookupService;
    @Mock
    private IS3Service s3Service;
    @Mock
    private WorkSessionMapper workSessionMapper;
    @Mock
    private INotificationPublisher notificationPublisher;
    @Mock
    private IBookingSettlementService bookingSettlementService;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NurseAvailabilityStatusSyncService availabilityStatusSyncService;

    private WorkSessionServiceImpl service;
    private UUID motherId;

    @BeforeEach
    void setUp() {
        service = new WorkSessionServiceImpl(
                workSessionRepository,
                checklistItemRepository,
                evidenceRepository,
                nurseProfileRepository,
                userAccountLookupService,
                s3Service,
                workSessionMapper,
                notificationPublisher,
                bookingSettlementService,
                auditRecorder,
                eventPublisher,
                availabilityStatusSyncService);
        motherId = UUID.randomUUID();
        when(userAccountLookupService.getCurrentUser()).thenReturn(User.builder().id(motherId).build());
    }

    @Test
    void getMyMotherWorkSessionsUsesUnfilteredPageWhenBucketIsBlank() {
        Pageable pageable = PageRequest.of(0, 10);
        WorkSession session = session(WorkSessionStatus.SCHEDULED);
        when(workSessionRepository.findByMotherId(motherId, pageable))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));
        stubEmptySessionDetails(session);

        Page<WorkSessionResponse> result = service.getMyMotherWorkSessions(" ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(workSessionRepository).findByMotherId(motherId, pageable);
        verify(workSessionRepository, never()).findByMotherIdAndStatusIn(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMyMotherWorkSessionsMapsUpcomingBucketToScheduledAndInProgress() {
        Pageable pageable = PageRequest.of(0, 10);
        WorkSession session = session(WorkSessionStatus.IN_PROGRESS);
        when(workSessionRepository.findByMotherIdAndStatusIn(eq(motherId), any(Collection.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));
        stubEmptySessionDetails(session);

        Page<WorkSessionResponse> result = service.getMyMotherWorkSessions("upcoming", pageable);

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Collection<WorkSessionStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workSessionRepository).findByMotherIdAndStatusIn(eq(motherId), statuses.capture(), eq(pageable));
        assertThat(statuses.getValue()).containsExactly(WorkSessionStatus.SCHEDULED, WorkSessionStatus.IN_PROGRESS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMyMotherWorkSessionsMapsActionNeededBucketToConfirmationAndReported() {
        Pageable pageable = PageRequest.of(0, 10);
        when(workSessionRepository.findByMotherIdAndStatusIn(eq(motherId), any(Collection.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.getMyMotherWorkSessions("ACTION_NEEDED", pageable);

        ArgumentCaptor<Collection<WorkSessionStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workSessionRepository).findByMotherIdAndStatusIn(eq(motherId), statuses.capture(), eq(pageable));
        assertThat(statuses.getValue())
                .containsExactly(WorkSessionStatus.PENDING_MOTHER_CONFIRMATION, WorkSessionStatus.REPORTED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMyMotherWorkSessionsMapsHistoryBucketToClosedStatuses() {
        Pageable pageable = PageRequest.of(0, 10);
        when(workSessionRepository.findByMotherIdAndStatusIn(eq(motherId), any(Collection.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.getMyMotherWorkSessions("HISTORY", pageable);

        ArgumentCaptor<Collection<WorkSessionStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(workSessionRepository).findByMotherIdAndStatusIn(eq(motherId), statuses.capture(), eq(pageable));
        assertThat(statuses.getValue())
                .containsExactly(
                        WorkSessionStatus.COMPLETED,
                        WorkSessionStatus.AUTO_CONFIRMED,
                        WorkSessionStatus.CANCELLED);
    }

    private WorkSession session(WorkSessionStatus status) {
        return WorkSession.builder()
                .id(UUID.randomUUID())
                .status(status)
                .build();
    }

    private WorkSessionResponse response(UUID id) {
        return WorkSessionResponse.builder()
                .id(id)
                .build();
    }

    private void stubEmptySessionDetails(WorkSession session) {
        when(evidenceRepository.findByWorkSession_IdAndEvidenceTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(checklistItemRepository.findByWorkSession_IdOrderBySortOrderAsc(session.getId()))
                .thenReturn(List.of());
        when(workSessionMapper.toResponse(eq(session), eq(List.of()), eq(List.of())))
                .thenReturn(response(session.getId()));
    }
}
