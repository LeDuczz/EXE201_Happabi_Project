package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.request.nurse.CreateNurseAvailabilityWindowRequest;
import com.minduc.happabi.dto.response.nurse.NurseAvailabilityWindowResponse;
import com.minduc.happabi.entity.NurseAvailabilityWindow;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseAvailabilityWindowStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseAvailabilityWindowRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.nurse.INurseAvailabilityWindowService;
import com.minduc.happabi.service.nurse.NurseAvailabilityStatusSyncService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NurseAvailabilityWindowServiceImpl implements INurseAvailabilityWindowService {

    private final NurseAvailabilityWindowRepository availabilityWindowRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final INotificationPublisher notificationPublisher;
    private final NurseAvailabilityStatusSyncService availabilityStatusSyncService;

    private static final DateTimeFormatter NOTIFICATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @LogExecution
    @TimedAction("GET_MY_NURSE_AVAILABILITY_WINDOWS")
    public List<NurseAvailabilityWindowResponse> getMyWindows() {
        NurseProfile nurseProfile = currentNurseProfile();
        availabilityStatusSyncService.syncStatus(nurseProfile);
        return availabilityWindowRepository.findByNurseProfile_IdAndStatusAndEndAtAfterOrderByStartAtAsc(
                        nurseProfile.getId(),
                        NurseAvailabilityWindowStatus.ACTIVE,
                        OffsetDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @LogExecution
    @TimedAction("CREATE_NURSE_AVAILABILITY_WINDOW")
    @AuditAction(action = "CREATE_NURSE_AVAILABILITY_WINDOW", resourceType = "NURSE_AVAILABILITY")
    public NurseAvailabilityWindowResponse createMyWindow(CreateNurseAvailabilityWindowRequest request) {
        validateWindow(request.getStartAt(), request.getEndAt());
        NurseProfile nurseProfile = currentNurseProfile();
        validateNotBookingSuspended(nurseProfile);
        validateNoActiveOverlap(nurseProfile, request.getStartAt(), request.getEndAt());

        NurseAvailabilityWindow saved = availabilityWindowRepository.save(NurseAvailabilityWindow.builder()
                .nurseProfile(nurseProfile)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(NurseAvailabilityWindowStatus.ACTIVE)
                .build());

        AvailabilityStatus nurseAvailabilityStatus = availabilityStatusSyncService.syncStatus(nurseProfile);
        notifyWindowOpened(saved);
        return toResponse(saved, nurseAvailabilityStatus);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('NURSE')")
    @LogExecution
    @TimedAction("CANCEL_NURSE_AVAILABILITY_WINDOW")
    @AuditAction(action = "CANCEL_NURSE_AVAILABILITY_WINDOW", resourceType = "NURSE_AVAILABILITY")
    public NurseAvailabilityWindowResponse cancelMyWindow(UUID windowId) {
        NurseProfile nurseProfile = currentNurseProfile();
        NurseAvailabilityWindow window = availabilityWindowRepository
                .findByIdAndNurseProfile_Id(windowId, nurseProfile.getId())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_AVAILABILITY_WINDOW_NOT_FOUND));

        if (window.getStatus() == NurseAvailabilityWindowStatus.ACTIVE) {
            window.setStatus(NurseAvailabilityWindowStatus.CANCELLED);
            window = availabilityWindowRepository.save(window);
        }
        AvailabilityStatus nurseAvailabilityStatus = availabilityStatusSyncService.syncStatus(nurseProfile);
        notifyWindowCancelled(window);
        return toResponse(window, nurseAvailabilityStatus);
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private void validateWindow(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new AppException(UserErrorCode.NURSE_AVAILABILITY_WINDOW_INVALID,
                    "End time must be after start time.");
        }
        if (endAt.isBefore(OffsetDateTime.now())) {
            throw new AppException(UserErrorCode.NURSE_AVAILABILITY_WINDOW_INVALID,
                    "Availability window must not end in the past.");
        }
    }

    private void validateNotBookingSuspended(NurseProfile nurseProfile) {
        if (nurseProfile.getBookingSuspendedUntil() != null && nurseProfile.getBookingSuspendedUntil().isAfter(OffsetDateTime.now())) {
            throw new AppException(UserErrorCode.NURSE_AVAILABILITY_WINDOW_INVALID,
                    "Nurse is suspended from receiving bookings until " + nurseProfile.getBookingSuspendedUntil());
        }
    }

    private void validateNoActiveOverlap(NurseProfile nurseProfile, OffsetDateTime startAt, OffsetDateTime endAt) {
        boolean hasOverlap = !availabilityWindowRepository.findOverlapping(
                        nurseProfile.getId(),
                        startAt,
                        endAt,
                        NurseAvailabilityWindowStatus.ACTIVE)
                .isEmpty();
        if (hasOverlap) {
            throw new AppException(UserErrorCode.NURSE_AVAILABILITY_WINDOW_INVALID,
                    "Availability window overlaps an active window.");
        }
    }

    private NurseAvailabilityWindowResponse toResponse(NurseAvailabilityWindow window) {
        return toResponse(window, null);
    }

    private NurseAvailabilityWindowResponse toResponse(NurseAvailabilityWindow window,
                                                       AvailabilityStatus nurseAvailabilityStatus) {
        return NurseAvailabilityWindowResponse.builder()
                .id(window.getId())
                .startAt(window.getStartAt())
                .endAt(window.getEndAt())
                .status(window.getStatus())
                .nurseAvailabilityStatus(nurseAvailabilityStatus)
                .build();
    }

    private void notifyWindowOpened(NurseAvailabilityWindow window) {
        notificationPublisher.publish(
                window.getNurseProfile().getUser().getId(),
                NotificationType.NURSE_AVAILABILITY_WINDOW_OPENED,
                "Availability window opened",
                "You opened an availability window from %s to %s."
                        .formatted(formatNotificationTime(window.getStartAt()), formatNotificationTime(window.getEndAt())),
                "NURSE_AVAILABILITY",
                window.getId().toString());
    }

    private void notifyWindowCancelled(NurseAvailabilityWindow window) {
        notificationPublisher.publish(
                window.getNurseProfile().getUser().getId(),
                NotificationType.NURSE_AVAILABILITY_WINDOW_CANCELLED,
                "Availability window cancelled",
                "You cancelled an availability window from %s to %s."
                        .formatted(formatNotificationTime(window.getStartAt()), formatNotificationTime(window.getEndAt())),
                "NURSE_AVAILABILITY",
                window.getId().toString());
    }

    private String formatNotificationTime(OffsetDateTime value) {
        return value.format(NOTIFICATION_TIME_FORMAT);
    }
}
