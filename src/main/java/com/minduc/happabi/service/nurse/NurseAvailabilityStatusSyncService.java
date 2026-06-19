package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseAvailabilityWindowStatus;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseAvailabilityWindowRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseAvailabilityStatusSyncService {

    private static final Set<WorkSessionStatus> BUSY_SESSION_STATUSES = EnumSet.of(
            WorkSessionStatus.SCHEDULED,
            WorkSessionStatus.IN_PROGRESS
    );

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseAvailabilityWindowRepository availabilityWindowRepository;
    private final WorkSessionRepository workSessionRepository;
    private final UserCacheService userCacheService;

    @Scheduled(fixedDelayString = "${app.nurse.availability-status-sync-fixed-delay-ms:60000}")
    @Transactional
    @LogExecution
    @TimedAction("SYNC_NURSE_AVAILABILITY_STATUSES")
    public void syncActiveNurseStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        nurseProfileRepository.findByNurseStatusOrderByUpdatedAtAsc(NurseStatus.ACTIVE)
                .forEach(profile -> syncStatus(profile, now));
    }

    @Transactional
    public AvailabilityStatus syncStatus(NurseProfile nurseProfile) {
        return syncStatus(nurseProfile, OffsetDateTime.now());
    }

    public AvailabilityStatus resolveStatus(UUID nurseProfileId, OffsetDateTime now) {
        if (isBookingSuspended(nurseProfileId, now)) {
            return AvailabilityStatus.OFFLINE;
        }
        if (isInCurrentBooking(nurseProfileId, now)) {
            return AvailabilityStatus.BUSY;
        }
        if (isInsideActiveAvailabilityWindow(nurseProfileId, now)) {
            return AvailabilityStatus.AVAILABLE;
        }
        return AvailabilityStatus.OFFLINE;
    }

    private AvailabilityStatus syncStatus(NurseProfile nurseProfile, OffsetDateTime now) {
        AvailabilityStatus nextStatus = resolveStatus(nurseProfile.getId(), now);
        if (nurseProfile.getAvailabilityStatus() != nextStatus) {
            AvailabilityStatus previous = nurseProfile.getAvailabilityStatus();
            nurseProfile.setAvailabilityStatus(nextStatus);
            nurseProfileRepository.saveAndFlush(nurseProfile);
            if (nurseProfile.getUser() != null && nurseProfile.getUser().getCognitoSub() != null) {
                userCacheService.evictProfiles(nurseProfile.getUser().getCognitoSub());
            }
            log.info("[NurseAvailabilityStatus] Synced nurseProfileId={} {} -> {}",
                    nurseProfile.getId(), previous, nextStatus);
        }
        return nextStatus;
    }

    private boolean isBookingSuspended(UUID nurseProfileId, OffsetDateTime now) {
        return nurseProfileRepository.findById(nurseProfileId)
                .map(profile -> profile.getBookingSuspendedUntil() != null && profile.getBookingSuspendedUntil().isAfter(now))
                .orElse(false);
    }

    private boolean isInCurrentBooking(UUID nurseProfileId, OffsetDateTime now) {
        return workSessionRepository.existsByNurseProfile_IdAndStatusInAndScheduledStartAtLessThanEqualAndScheduledEndAtGreaterThan(
                nurseProfileId,
                BUSY_SESSION_STATUSES,
                now,
                now);
    }

    private boolean isInsideActiveAvailabilityWindow(UUID nurseProfileId, OffsetDateTime now) {
        return availabilityWindowRepository.existsCovering(
                nurseProfileId,
                now,
                now,
                NurseAvailabilityWindowStatus.ACTIVE);
    }
}
