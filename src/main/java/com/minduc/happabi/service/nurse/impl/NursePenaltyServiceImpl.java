package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.INursePenaltyService;
import com.minduc.happabi.service.nurse.NurseAccessCacheService;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NursePenaltyServiceImpl implements INursePenaltyService {

    private static final int PERMANENT_SUSPENSION_THRESHOLD = 4;

    private final NurseProfileRepository nurseProfileRepository;
    private final INotificationPublisher notificationPublisher;
    private final NurseAccessCacheService nurseAccessCacheService;
    private final UserCacheService userCacheService;

    @Override
    @LogExecution
    @TimedAction("APPLY_NURSE_NO_SHOW_PENALTY")
    @AuditAction(action = "APPLY_NURSE_NO_SHOW_PENALTY", resourceType = "NURSE_PENALTY")
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyNoShowPenalty(WorkSession session, String reason) {
        if (session == null || session.getNurseProfile() == null) {
            throw new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND);
        }

        NurseProfile nurse = nurseProfileRepository.findByIdForUpdate(session.getNurseProfile().getId())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();
        int nextViolationCount = normalize(nurse.getNoShowViolationCount()) + 1;
        nurse.setNoShowViolationCount(nextViolationCount);
        nurse.setAvailabilityStatus(AvailabilityStatus.OFFLINE);

        String cleanReason = normalizeReason(reason);
        if (nextViolationCount >= PERMANENT_SUSPENSION_THRESHOLD) {
            nurse.setNurseStatus(NurseStatus.SUSPENDED);
            nurse.setPermanentlySuspendedAt(now);
            nurse.setBookingSuspendedUntil(null);
            nurse.setBookingSuspensionReason(cleanReason);
            nurse.setLastStatusChangedAt(now);
        } else {
            int days = suspensionDays(nextViolationCount);
            OffsetDateTime base = session.getScheduledStartAt() == null || now.isAfter(session.getScheduledStartAt())
                    ? now
                    : session.getScheduledStartAt();
            nurse.setBookingSuspendedUntil(base.plusDays(days));
            nurse.setBookingSuspensionReason(cleanReason);
        }

        nurseProfileRepository.save(nurse);
        evictCaches(nurse);
        notifyNurse(nurse, nextViolationCount);
        log.info("[NursePenalty] Applied no-show penalty nurseProfileId={} violationCount={} suspendedUntil={} status={}",
                nurse.getId(), nextViolationCount, nurse.getBookingSuspendedUntil(), nurse.getNurseStatus());
    }

    private int suspensionDays(int violationCount) {
        return switch (violationCount) {
            case 1 -> 3;
            case 2 -> 7;
            case 3 -> 10;
            default -> 0;
        };
    }

    private int normalize(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Nurse no-show confirmed by admin.";
        }
        return reason.trim();
    }

    private void evictCaches(NurseProfile nurse) {
        if (nurse.getUser() == null) {
            return;
        }
        nurseAccessCacheService.evict(nurse.getUser().getId());
        if (nurse.getUser().getCognitoSub() != null) {
            userCacheService.evictProfiles(nurse.getUser().getCognitoSub());
        }
    }

    private void notifyNurse(NurseProfile nurse, int violationCount) {
        if (nurse.getUser() == null) {
            return;
        }
        boolean permanent = violationCount >= PERMANENT_SUSPENSION_THRESHOLD;
        String message = permanent
                ? "Your account has been suspended because of repeated no-show violations."
                : "Your booking availability has been suspended until %s because of a confirmed no-show violation."
                        .formatted(nurse.getBookingSuspendedUntil());
        notificationPublisher.publish(
                nurse.getUser().getId(),
                NotificationType.NURSE_SUSPENDED,
                permanent ? "Nurse account suspended" : "Booking availability suspended",
                message,
                "NURSE_PENALTY",
                nurse.getId().toString());
    }
}