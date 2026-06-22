package com.minduc.happabi.service.notification.impl;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.notification.INurseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NurseNotificationService implements INurseNotificationService {

    private static final String RESOURCE_TYPE = "NURSE_PROFILE";

    private final INotificationPublisher notificationPublisher;

    @Override
    public void notifyRejected(NurseProfile profile, String reason) {
        publish(
                profile,
                NotificationType.NURSE_PROFILE_REJECTED,
                "Nurse profile needs updates",
                reason == null || reason.isBlank()
                        ? "Your nurse profile was not approved. Please review the requirements and submit it again."
                        : reason,
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    @Override
    public void notifyApprovedPendingContract(NurseProfile profile) {
        publish(
                profile,
                NotificationType.NURSE_PROFILE_APPROVED_PENDING_CONTRACT,
                "Nurse profile approved",
                "Please review and sign your contract to activate your nurse account.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    @Override
    public void notifyActive(NurseProfile profile) {
        publish(
                profile,
                NotificationType.NURSE_PROFILE_ACTIVE,
                "Nurse account activated",
                "You can now accept bookings and use Happabi.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    @Override
    public void notifySuspended(NurseProfile profile, String reason) {
        publish(
                profile,
                NotificationType.NURSE_SUSPENDED,
                "Nurse account temporarily suspended",
                reason == null || reason.isBlank()
                        ? "Your nurse account has been temporarily suspended."
                        : reason,
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    @Override
    public void notifyReactivated(NurseProfile profile) {
        publish(
                profile,
                NotificationType.NURSE_REACTIVATED,
                "Nurse account reactivated",
                "You can resume accepting bookings on Happabi.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    private void publish(NurseProfile profile,
                         NotificationType type,
                         String title,
                         String message,
                         String resourceType,
                         String resourceId) {
        if (profile == null || profile.getUser() == null || profile.getUser().getId() == null) {
            return;
        }
        notificationPublisher.publish(
                profile.getUser().getId(),
                type,
                title,
                message,
                resourceType,
                resourceId
        );
    }
}
