package com.minduc.happabi.service.worksession.impl;

import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.dto.request.worksession.CompleteChecklistItemRequest;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionChecklistItemResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionEvidenceResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.entity.WorkSessionChecklistItem;
import com.minduc.happabi.entity.WorkSessionEvidence;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.WorkSessionChecklistStatus;
import com.minduc.happabi.enums.WorkSessionEvidenceStatus;
import com.minduc.happabi.enums.WorkSessionEvidenceType;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.exception.code.WorkSessionErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.integration.sqs.IFileCleanupPublisher;
import com.minduc.happabi.mapper.WorkSessionMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WorkSessionChecklistItemRepository;
import com.minduc.happabi.repository.WorkSessionEvidenceRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.booking.IBookingSettlementService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import com.minduc.happabi.service.worksession.IWorkSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkSessionServiceImpl implements IWorkSessionService {

    private static final String EVIDENCE_FOLDER = "work-sessions";
    private static final Collection<WorkSessionEvidenceStatus> UNDO_DELETABLE_STATUSES = List.of(
            WorkSessionEvidenceStatus.ACTIVE,
            WorkSessionEvidenceStatus.DELETE_PENDING
    );

    private final WorkSessionRepository workSessionRepository;
    private final WorkSessionChecklistItemRepository checklistItemRepository;
    private final WorkSessionEvidenceRepository evidenceRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final IS3Service s3Service;
    private final WorkSessionMapper workSessionMapper;
    private final INotificationPublisher notificationPublisher;
    private final IBookingSettlementService bookingSettlementService;
    private final ApplicationEventPublisher eventPublisher;
    private final IFileCleanupPublisher fileCleanupPublisher;

    @Value("${app.work-session.check-in-open-minutes:10}")
    private long checkInOpenMinutes;

    @Value("${app.work-session.auto-confirm-hours:48}")
    private long autoConfirmHours;

    @Value("${app.work-session.evidence-retention-days:60}")
    private long evidenceRetentionDays;

    @Scheduled(fixedDelayString = "${app.work-session.auto-confirm-fixed-delay-ms:300000}")
    @TimedAction("AUTO_CONFIRM_WORK_SESSIONS")
    @AuditAction(action = "AUTO_CONFIRM_WORK_SESSIONS", resourceType = "WORK_SESSION")
    @Transactional
    public void autoConfirmExpiredSessions() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> ids = workSessionRepository.findIdsReadyForAutoConfirm(
                WorkSessionStatus.PENDING_MOTHER_CONFIRMATION, now);
        for (UUID id : ids) {
            int updated = workSessionRepository.autoConfirm(
                    id,
                    WorkSessionStatus.PENDING_MOTHER_CONFIRMATION,
                    WorkSessionStatus.AUTO_CONFIRMED,
                    now);
            if (updated == 1) {
                WorkSession session = getSessionForUpdate(id);
                bookingSettlementService.settleCompletedWorkSession(session);
                log.info("[WorkSession] Auto-confirmed session id={}", id);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.work-session.evidence-cleanup-fixed-delay-ms:3600000}")
    @Transactional
    public void cleanupExpiredEvidence() {
        OffsetDateTime now = OffsetDateTime.now();
        List<WorkSessionEvidence> evidences = evidenceRepository
                .findTop100ByStatusAndRetentionUntilBeforeOrderByRetentionUntilAsc(
                        WorkSessionEvidenceStatus.ACTIVE, now);
        for (WorkSessionEvidence evidence : evidences) {
            evidence.setStatus(WorkSessionEvidenceStatus.DELETE_PENDING);
            evidence.setDeleteRequestedAt(now);
            eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(
                    evidence.getS3Key(), "WORK_SESSION_EVIDENCE_RETENTION_EXPIRED:" + evidence.getId()));
        }
        evidenceRepository.saveAll(evidences);
    }

    @Override
    @Transactional
    public WorkSession createFromAcceptedBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new AppException(BookingErrorCode.BOOKING_CREATE_FAILED, "Booking is missing.");
        }
        if (workSessionRepository.existsByBooking_Id(booking.getId())) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_ALREADY_EXISTS);
        }

        WorkSession session = WorkSession.builder()
                .booking(booking)
                .mother(booking.getMother())
                .nurseProfile(booking.getNurseProfile())
                .serviceOffering(booking.getServiceOffering())
                .scheduledStartAt(booking.getStartAt())
                .scheduledEndAt(booking.getEndAt())
                .status(WorkSessionStatus.SCHEDULED)
                .build();
        WorkSession saved = workSessionRepository.save(session);
        checklistItemRepository.saveAll(buildChecklistItems(saved));
        notifyBookingConfirmed(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE')")
    public List<WorkSessionResponse> getMyNurseWorkSessions() {
        NurseProfile nurseProfile = currentNurseProfile();
        return workSessionRepository.findByNurseProfileId(nurseProfile.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE')")
    public WorkSessionResponse getMyNurseWorkSession(UUID workSessionId) {
        WorkSession session = getSession(workSessionId);
        ensureNurseOwns(session, currentNurseProfile());
        return toResponse(session);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    public WorkSessionResponse checkIn(UUID workSessionId, List<MultipartFile> images) {
        requireImages(images);
        WorkSession session = getSessionForUpdate(workSessionId);
        NurseProfile nurseProfile = currentNurseProfile();
        ensureNurseOwns(session, nurseProfile);
        requireStatus(session, WorkSessionStatus.SCHEDULED);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime opensAt = session.getScheduledStartAt().minusMinutes(checkInOpenMinutes);
        if (now.isBefore(opensAt)) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_CHECK_IN_TOO_EARLY,
                    "Check-in opens at " + opensAt);
        }

        List<String> uploadedKeys = new ArrayList<>();
        try {
            List<WorkSessionEvidence> evidences = uploadEvidence(session, null,
                    WorkSessionEvidenceType.CHECK_IN, images, uploadedKeys);
            evidenceRepository.saveAll(evidences);

            session.setCheckedInAt(now);
            session.setLateMinutes(calculateLateMinutes(session.getScheduledStartAt(), now));
            session.setStatus(WorkSessionStatus.IN_PROGRESS);
            nurseProfile.setAvailabilityStatus(AvailabilityStatus.BUSY);
            WorkSession saved = workSessionRepository.save(session);

            notifyMother(session,
                    "Nurse checked in",
                    session.getLateMinutes() > 0
                            ? "Your nurse checked in " + session.getLateMinutes() + " minute(s) late."
                            : "Your nurse has checked in for the work session.");
            return toResponse(saved);
        } catch (RuntimeException e) {
            cleanupUploadedKeys(uploadedKeys, "WORK_SESSION_CHECK_IN_ROLLBACK");
            throw e;
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    public WorkSessionResponse completeChecklistItem(UUID workSessionId,
                                                     UUID checklistItemId,
                                                     CompleteChecklistItemRequest request,
                                                     List<MultipartFile> images) {
        requireImages(images);
        WorkSession session = getSessionForUpdate(workSessionId);
        ensureNurseOwns(session, currentNurseProfile());
        requireStatus(session, WorkSessionStatus.IN_PROGRESS);
        WorkSessionChecklistItem item = getChecklistItemForUpdate(workSessionId, checklistItemId);

        List<String> uploadedKeys = new ArrayList<>();
        try {
            List<WorkSessionEvidence> evidences = uploadEvidence(session, item,
                    WorkSessionEvidenceType.CHECKLIST_ITEM, images, uploadedKeys);
            evidenceRepository.saveAll(evidences);

            item.setStatus(WorkSessionChecklistStatus.COMPLETED);
            item.setCompletedAt(OffsetDateTime.now());
            item.setNote(normalize(request == null ? null : request.getNote()));
            checklistItemRepository.save(item);
            return toResponse(session);
        } catch (RuntimeException e) {
            cleanupUploadedKeys(uploadedKeys, "WORK_SESSION_CHECKLIST_ROLLBACK");
            throw e;
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    public WorkSessionResponse undoChecklistItem(UUID workSessionId, UUID checklistItemId) {
        WorkSession session = getSessionForUpdate(workSessionId);
        ensureNurseOwns(session, currentNurseProfile());
        requireStatus(session, WorkSessionStatus.IN_PROGRESS);
        WorkSessionChecklistItem item = getChecklistItemForUpdate(workSessionId, checklistItemId);

        item.setStatus(WorkSessionChecklistStatus.PENDING);
        item.setCompletedAt(null);
        item.setNote(null);
        checklistItemRepository.save(item);

        OffsetDateTime now = OffsetDateTime.now();
        List<WorkSessionEvidence> evidences = evidenceRepository.findByChecklistItem_IdAndStatusIn(
                item.getId(), UNDO_DELETABLE_STATUSES);
        for (WorkSessionEvidence evidence : evidences) {
            if (evidence.getStatus() != WorkSessionEvidenceStatus.DELETE_PENDING) {
                evidence.setStatus(WorkSessionEvidenceStatus.DELETE_PENDING);
                evidence.setDeleteRequestedAt(now);
                eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(
                        evidence.getS3Key(), "WORK_SESSION_CHECKLIST_UNDO:" + item.getId()));
            }
        }
        evidenceRepository.saveAll(evidences);
        return toResponse(session);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    public WorkSessionResponse checkout(UUID workSessionId) {
        WorkSession session = getSessionForUpdate(workSessionId);
        NurseProfile nurseProfile = currentNurseProfile();
        ensureNurseOwns(session, nurseProfile);
        requireStatus(session, WorkSessionStatus.IN_PROGRESS);

        long pendingItems = checklistItemRepository.countByWorkSession_IdAndStatus(
                session.getId(), WorkSessionChecklistStatus.PENDING);
        if (pendingItems > 0) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_CHECKLIST_INCOMPLETE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime retentionUntil = now.plusDays(evidenceRetentionDays);
        session.setCheckedOutAt(now);
        session.setAutoConfirmAt(now.plusHours(autoConfirmHours));
        session.setStatus(WorkSessionStatus.PENDING_MOTHER_CONFIRMATION);
        nurseProfile.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        List<WorkSessionEvidence> activeEvidences = evidenceRepository
                .findByWorkSession_IdAndStatusOrderByCreatedAtAsc(session.getId(), WorkSessionEvidenceStatus.ACTIVE);
        activeEvidences.forEach(evidence -> evidence.setRetentionUntil(retentionUntil));
        evidenceRepository.saveAll(activeEvidences);

        WorkSession saved = workSessionRepository.save(session);
        notifyMother(session,
                "Work session completed",
                "Your nurse has checked out. Please confirm completion or report an issue.");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public List<WorkSessionResponse> getMyMotherWorkSessions() {
        UUID motherId = userAccountLookupService.getCurrentUser().getId();
        return workSessionRepository.findByMotherId(motherId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public WorkSessionResponse getMyMotherWorkSession(UUID workSessionId) {
        WorkSession session = getSession(workSessionId);
        ensureMotherOwns(session);
        return toResponse(session);
    }

    @Override
    @TimedAction("CONFIRM_WORK_SESSION_BY_MOTHER")
    @AuditAction(action = "CONFIRM_WORK_SESSION_BY_MOTHER", resourceType = "WORK_SESSION")
    @Transactional
    @PreAuthorize("hasRole('MOTHER')")
    public WorkSessionResponse confirmByMother(UUID workSessionId) {
        WorkSession session = getSessionForUpdate(workSessionId);
        ensureMotherOwns(session);
        requireStatus(session, WorkSessionStatus.PENDING_MOTHER_CONFIRMATION);
        session.setStatus(WorkSessionStatus.COMPLETED);
        session.setConfirmedAt(OffsetDateTime.now());
        WorkSession saved = workSessionRepository.save(session);
        bookingSettlementService.settleCompletedWorkSession(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MOTHER')")
    public WorkSessionResponse reportByMother(UUID workSessionId, ReportWorkSessionRequest request) {
        WorkSession session = getSessionForUpdate(workSessionId);
        ensureMotherOwns(session);
        requireStatus(session, WorkSessionStatus.PENDING_MOTHER_CONFIRMATION);
        session.setStatus(WorkSessionStatus.REPORTED);
        session.setReportedAt(OffsetDateTime.now());
        session.setReportReason(request.getReason().trim());
        notifyNurse(session,
                "Work session reported",
                "The mother reported an issue with this work session.");
        return toResponse(workSessionRepository.save(session));
    }

    private WorkSession getSession(UUID id) {
        return workSessionRepository.findById(id)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_NOT_FOUND));
    }

    private WorkSession getSessionForUpdate(UUID id) {
        return workSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_NOT_FOUND));
    }

    private WorkSessionChecklistItem getChecklistItemForUpdate(UUID workSessionId, UUID checklistItemId) {
        return checklistItemRepository.findByIdAndWorkSessionIdForUpdate(checklistItemId, workSessionId)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_CHECKLIST_ITEM_NOT_FOUND));
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private void ensureNurseOwns(WorkSession session, NurseProfile nurseProfile) {
        if (!session.getNurseProfile().getId().equals(nurseProfile.getId())) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_ACCESS_DENIED);
        }
    }

    private void ensureMotherOwns(WorkSession session) {
        UUID currentUserId = userAccountLookupService.getCurrentUser().getId();
        if (!session.getMother().getId().equals(currentUserId)) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_ACCESS_DENIED);
        }
    }

    private void requireStatus(WorkSession session, WorkSessionStatus expected) {
        if (session.getStatus() != expected) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INVALID_STATE,
                    "Current status is " + session.getStatus() + ", expected " + expected);
        }
    }

    private void requireImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_EVIDENCE_REQUIRED);
        }
    }

    private List<WorkSessionEvidence> uploadEvidence(WorkSession session,
                                                     WorkSessionChecklistItem item,
                                                     WorkSessionEvidenceType type,
                                                     List<MultipartFile> files,
                                                     List<String> uploadedKeys) {
        List<WorkSessionEvidence> evidences = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String key = s3Service.upload(EVIDENCE_FOLDER, session.getId().toString(), file);
            uploadedKeys.add(key);
            evidences.add(WorkSessionEvidence.builder()
                    .workSession(session)
                    .checklistItem(item)
                    .evidenceType(type)
                    .status(WorkSessionEvidenceStatus.ACTIVE)
                    .s3Key(key)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build());
        }
        if (evidences.isEmpty()) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_EVIDENCE_REQUIRED);
        }
        return evidences;
    }

    private void cleanupUploadedKeys(List<String> uploadedKeys, String reason) {
        uploadedKeys.forEach(key -> fileCleanupPublisher.publishDeleteObject(key, reason));
    }

    private List<WorkSessionChecklistItem> buildChecklistItems(WorkSession session) {
        List<String> titles = checklistTitles(session.getServiceOffering().getPackageContent(),
                session.getServiceOffering().getServiceName());
        List<WorkSessionChecklistItem> items = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            items.add(WorkSessionChecklistItem.builder()
                    .workSession(session)
                    .title(titles.get(index))
                    .sortOrder(index + 1)
                    .status(WorkSessionChecklistStatus.PENDING)
                    .build());
        }
        return items;
    }

    private List<String> checklistTitles(String packageContent, String fallbackName) {
        if (packageContent == null || packageContent.isBlank()) {
            return List.of(fallbackName == null || fallbackName.isBlank() ? "Complete booked service" : fallbackName);
        }
        List<String> titles = Arrays.stream(packageContent.split("[\\n,;]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return titles.isEmpty() ? List.of(fallbackName) : titles;
    }

    private int calculateLateMinutes(OffsetDateTime scheduledStartAt, OffsetDateTime checkedInAt) {
        if (!checkedInAt.isAfter(scheduledStartAt)) {
            return 0;
        }
        return Math.toIntExact(Duration.between(scheduledStartAt, checkedInAt).toMinutes());
    }

    private WorkSessionResponse toResponse(WorkSession session) {
        List<WorkSessionEvidenceResponse> checkInEvidences = evidenceRepository
                .findByWorkSession_IdAndEvidenceTypeAndStatus(
                        session.getId(), WorkSessionEvidenceType.CHECK_IN, WorkSessionEvidenceStatus.ACTIVE)
                .stream()
                .map(this::toEvidenceResponse)
                .toList();
        List<WorkSessionChecklistItemResponse> checklistItems = checklistItemRepository
                .findByWorkSession_IdOrderBySortOrderAsc(session.getId())
                .stream()
                .map(item -> workSessionMapper.toChecklistItemResponse(
                        item,
                        evidenceRepository.findByChecklistItem_IdAndStatusOrderByCreatedAtAsc(
                                        item.getId(), WorkSessionEvidenceStatus.ACTIVE)
                                .stream()
                                .map(this::toEvidenceResponse)
                                .toList()))
                .toList();
        return workSessionMapper.toResponse(session, checkInEvidences, checklistItems);
    }

    private WorkSessionEvidenceResponse toEvidenceResponse(WorkSessionEvidence evidence) {
        return workSessionMapper.toEvidenceResponse(evidence, s3Service.presign(evidence.getS3Key()));
    }

    private void notifyMother(WorkSession session, String title, String message) {
        notificationPublisher.publish(
                session.getMother().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "WORK_SESSION",
                session.getId().toString()
        );
    }

    private void notifyNurse(WorkSession session, String title, String message) {
        notificationPublisher.publish(
                session.getNurseProfile().getUser().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "WORK_SESSION",
                session.getId().toString()
        );
    }

    private void notifyBookingConfirmed(WorkSession session) {
        notificationPublisher.publish(
                session.getMother().getId(),
                NotificationType.BOOKING_PAYMENT_SUCCESS,
                "Booking confirmed",
                "Your payment was successful. Your session with %s is scheduled for %s."
                        .formatted(session.getNurseProfile().getUser().getFullName(),
                                session.getScheduledStartAt()),
                "WORK_SESSION",
                session.getId().toString()
        );
        notificationPublisher.publish(
                session.getNurseProfile().getUser().getId(),
                NotificationType.NURSE_BOOKING_ASSIGNED,
                "New booking assigned",
                "You have a new session for %s at %s."
                        .formatted(session.getServiceOffering().getServiceName(),
                                session.getScheduledStartAt()),
                "WORK_SESSION",
                session.getId().toString()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

