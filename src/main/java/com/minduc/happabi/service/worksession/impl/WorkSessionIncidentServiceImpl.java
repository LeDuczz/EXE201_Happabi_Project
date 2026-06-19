package com.minduc.happabi.service.worksession.impl;

import com.minduc.happabi.dto.request.admin.ReviewWorkSessionIncidentRequest;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionIncidentRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionIncidentEvidenceResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionIncidentResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.entity.WorkSessionIncident;
import com.minduc.happabi.entity.WorkSessionIncidentEvidence;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import com.minduc.happabi.enums.WorkSessionIncidentType;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.exception.code.WorkSessionErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.integration.sqs.IFileCleanupPublisher;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.WorkSessionIncidentEvidenceRepository;
import com.minduc.happabi.repository.WorkSessionIncidentRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.INursePenaltyService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import com.minduc.happabi.service.worksession.IWorkSessionIncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkSessionIncidentServiceImpl implements IWorkSessionIncidentService {

    private static final String INCIDENT_EVIDENCE_FOLDER = "work-sessions";

    private final WorkSessionRepository workSessionRepository;
    private final WorkSessionIncidentRepository incidentRepository;
    private final WorkSessionIncidentEvidenceRepository incidentEvidenceRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final INotificationPublisher notificationPublisher;
    private final IS3Service s3Service;
    private final IFileCleanupPublisher fileCleanupPublisher;
    private final INursePenaltyService nursePenaltyService;

    @Value("${app.work-session.mother-unreachable-report-open-minutes:15}")
    private long motherUnreachableReportOpenMinutes;

    @Override
    @LogExecution
    @TimedAction("REPORT_MOTHER_UNREACHABLE")
    @AuditAction(action = "REPORT_MOTHER_UNREACHABLE", resourceType = "WORK_SESSION_INCIDENT")
    @Transactional
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public WorkSessionIncidentResponse reportMotherUnreachable(UUID workSessionId,
                                                               ReportWorkSessionIncidentRequest request,
                                                               List<MultipartFile> images) {
        requireImages(images);
        NurseProfile nurseProfile = currentNurseProfile();
        WorkSession session = workSessionRepository.findByIdForUpdate(workSessionId)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_NOT_FOUND));
        ensureNurseOwns(session, nurseProfile);
        if (session.getStatus() != WorkSessionStatus.SCHEDULED && session.getStatus() != WorkSessionStatus.IN_PROGRESS) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INVALID_STATE);
        }
        OffsetDateTime reportOpenAt = session.getScheduledStartAt().plusMinutes(motherUnreachableReportOpenMinutes);
        if (OffsetDateTime.now().isBefore(reportOpenAt)) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INCIDENT_TOO_EARLY,
                    "Mother unreachable report opens at " + reportOpenAt);
        }

        WorkSessionIncident incident = incidentRepository.save(WorkSessionIncident.builder()
                .workSession(session)
                .reportedBy(nurseProfile.getUser())
                .incidentType(WorkSessionIncidentType.MOTHER_UNREACHABLE)
                .status(WorkSessionIncidentStatus.PENDING_REVIEW)
                .description(cleanDescription(request))
                .build());

        List<String> uploadedKeys = new ArrayList<>();
        try {
            List<WorkSessionIncidentEvidence> evidences = uploadEvidences(incident, images, uploadedKeys);
            incidentEvidenceRepository.saveAll(evidences);
            session.setStatus(WorkSessionStatus.REPORTED);
            session.setReportedAt(OffsetDateTime.now());
            session.setReportReason(incident.getDescription());
            workSessionRepository.save(session);
            notifyMother(session,
                    "Mother unreachable incident reported",
                    "The nurse reported that they could not reach you after arrival. Admin will review the evidence.");
            notifyAdmins(incident);
            log.info("[WorkSessionIncident] Mother unreachable reported incidentId={} workSessionId={}",
                    incident.getId(), session.getId());
            return toResponse(incident);
        } catch (RuntimeException e) {
            uploadedKeys.forEach(key -> fileCleanupPublisher.publishDeleteObject(key, "WORK_SESSION_INCIDENT_ROLLBACK"));
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<WorkSessionIncidentResponse> getIncidents(WorkSessionIncidentStatus status, Pageable pageable) {
        Page<WorkSessionIncident> page = status == null
                ? incidentRepository.findAllByOrderByCreatedAtDesc(pageable)
                : incidentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @LogExecution
    @TimedAction("APPROVE_WORK_SESSION_INCIDENT")
    @AuditAction(action = "APPROVE_WORK_SESSION_INCIDENT", resourceType = "WORK_SESSION_INCIDENT")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkSessionIncidentResponse approveIncident(UUID incidentId, ReviewWorkSessionIncidentRequest request) {
        WorkSessionIncident incident = lockIncident(incidentId);
        requirePending(incident);
        User admin = userAccountLookupService.getCurrentUser();
        incident.setStatus(WorkSessionIncidentStatus.APPROVED);
        incident.setReviewedByAdmin(admin);
        incident.setReviewedAt(OffsetDateTime.now());
        incident.setAdminNote(cleanOptional(request == null ? null : request.getAdminNote()));
        WorkSession session = incident.getWorkSession();
        session.setStatus(WorkSessionStatus.CANCELLED);
        session.getBooking().setStatus(BookingStatus.CANCELLED);
        bookingSlotRepository.releaseByBookingId(session.getBooking().getId(), BookingSlotStatus.AVAILABLE);
        workSessionRepository.save(session);
        if (incident.getIncidentType() == WorkSessionIncidentType.NURSE_NO_SHOW) {
            nursePenaltyService.applyNoShowPenalty(session, incident.getAdminNote());
        }
        notifyMother(session,
                "Incident approved",
                "Admin approved the incident report. This work session has been closed according to policy.");
        notifyNurse(session,
                "Incident approved",
                "Admin approved your incident report.");
        return toResponse(incidentRepository.save(incident));
    }

    @Override
    @LogExecution
    @TimedAction("REJECT_WORK_SESSION_INCIDENT")
    @AuditAction(action = "REJECT_WORK_SESSION_INCIDENT", resourceType = "WORK_SESSION_INCIDENT")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkSessionIncidentResponse rejectIncident(UUID incidentId, ReviewWorkSessionIncidentRequest request) {
        WorkSessionIncident incident = lockIncident(incidentId);
        requirePending(incident);
        User admin = userAccountLookupService.getCurrentUser();
        incident.setStatus(WorkSessionIncidentStatus.REJECTED);
        incident.setReviewedByAdmin(admin);
        incident.setReviewedAt(OffsetDateTime.now());
        incident.setAdminNote(cleanOptional(request == null ? null : request.getAdminNote()));
        WorkSession session = incident.getWorkSession();
        notifyMother(session,
                "Incident rejected",
                "Admin rejected the incident report. Please follow up with support if needed.");
        notifyNurse(session,
                "Incident rejected",
                "Admin rejected your incident report. Reason: " + (incident.getAdminNote() == null ? "No note" : incident.getAdminNote()));
        return toResponse(incidentRepository.save(incident));
    }

    @Override
    @LogExecution
    @TimedAction("MARK_NURSE_NO_SHOW")
    @AuditAction(action = "MARK_NURSE_NO_SHOW", resourceType = "WORK_SESSION_INCIDENT")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkSessionIncidentResponse markNurseNoShow(UUID workSessionId, ReviewWorkSessionIncidentRequest request) {
        WorkSession session = workSessionRepository.findByIdForUpdate(workSessionId)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_NOT_FOUND));
        if (session.getStatus() == WorkSessionStatus.COMPLETED || session.getStatus() == WorkSessionStatus.AUTO_CONFIRMED) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INVALID_STATE);
        }

        User admin = userAccountLookupService.getCurrentUser();
        String note = cleanOptional(request == null ? null : request.getAdminNote());
        WorkSessionIncident incident = incidentRepository.save(WorkSessionIncident.builder()
                .workSession(session)
                .reportedBy(admin)
                .incidentType(WorkSessionIncidentType.NURSE_NO_SHOW)
                .status(WorkSessionIncidentStatus.APPROVED)
                .description(note == null ? "Nurse no-show confirmed by admin." : note)
                .reviewedByAdmin(admin)
                .reviewedAt(OffsetDateTime.now())
                .adminNote(note)
                .build());

        session.setStatus(WorkSessionStatus.CANCELLED);
        session.getBooking().setStatus(BookingStatus.CANCELLED);
        bookingSlotRepository.releaseByBookingId(session.getBooking().getId(), BookingSlotStatus.AVAILABLE);
        workSessionRepository.save(session);
        nursePenaltyService.applyNoShowPenalty(session, incident.getDescription());
        notifyMother(session,
                "Nurse no-show confirmed",
                "Admin confirmed that the nurse did not attend this work session. Support will follow up according to policy.");
        notifyNurse(session,
                "No-show penalty applied",
                "A no-show violation has been confirmed for this work session. Your booking availability has been restricted according to policy.");
        return toResponse(incident);
    }
    private WorkSessionIncident lockIncident(UUID incidentId) {
        return incidentRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new AppException(WorkSessionErrorCode.WORK_SESSION_INCIDENT_NOT_FOUND));
    }

    private void requirePending(WorkSessionIncident incident) {
        if (incident.getStatus() != WorkSessionIncidentStatus.PENDING_REVIEW) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INCIDENT_NOT_PENDING);
        }
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

    private void requireImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_EVIDENCE_REQUIRED);
        }
    }

    private String cleanDescription(ReportWorkSessionIncidentRequest request) {
        String description = request == null ? null : request.getDescription();
        if (description == null || description.isBlank()) {
            throw new AppException(WorkSessionErrorCode.WORK_SESSION_INVALID_STATE, "Incident description is required.");
        }
        return description.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<WorkSessionIncidentEvidence> uploadEvidences(WorkSessionIncident incident,
                                                              List<MultipartFile> files,
                                                              List<String> uploadedKeys) {
        List<WorkSessionIncidentEvidence> evidences = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String key = s3Service.upload(INCIDENT_EVIDENCE_FOLDER, incident.getWorkSession().getId().toString(), file);
            uploadedKeys.add(key);
            evidences.add(WorkSessionIncidentEvidence.builder()
                    .incident(incident)
                    .s3Key(key)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build());
        }
        return evidences;
    }

    private void notifyAdmins(WorkSessionIncident incident) {
        userRepository.findActiveUsersByRoleName(UserRole.ADMIN).forEach(admin ->
                notificationPublisher.publish(
                        admin.getId(),
                        NotificationType.WORK_SESSION_UPDATED,
                        "Work session incident reported",
                        "A nurse reported a mother unreachable incident for work session %s."
                                .formatted(incident.getWorkSession().getId()),
                        "WORK_SESSION_INCIDENT",
                        incident.getId().toString()));
    }

    private void notifyMother(WorkSession session, String title, String message) {
        notificationPublisher.publish(
                session.getMother().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "WORK_SESSION",
                session.getId().toString());
    }

    private void notifyNurse(WorkSession session, String title, String message) {
        notificationPublisher.publish(
                session.getNurseProfile().getUser().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "WORK_SESSION",
                session.getId().toString());
    }

    private WorkSessionIncidentResponse toResponse(WorkSessionIncident incident) {
        return WorkSessionIncidentResponse.builder()
                .id(incident.getId())
                .workSessionId(incident.getWorkSession().getId())
                .incidentType(incident.getIncidentType())
                .status(incident.getStatus())
                .description(incident.getDescription())
                .reportedByName(incident.getReportedBy().getFullName())
                .adminNote(incident.getAdminNote())
                .reviewedAt(incident.getReviewedAt())
                .createdAt(incident.getCreatedAt())
                .evidences(incidentEvidenceRepository.findByIncident_IdOrderByCreatedAtAsc(incident.getId()).stream()
                        .map(this::toEvidenceResponse)
                        .toList())
                .build();
    }

    private WorkSessionIncidentEvidenceResponse toEvidenceResponse(WorkSessionIncidentEvidence evidence) {
        return WorkSessionIncidentEvidenceResponse.builder()
                .id(evidence.getId())
                .previewUrl(s3Service.presign(evidence.getS3Key()))
                .contentType(evidence.getContentType())
                .fileSize(evidence.getFileSize())
                .createdAt(evidence.getCreatedAt())
                .build();
    }
}
