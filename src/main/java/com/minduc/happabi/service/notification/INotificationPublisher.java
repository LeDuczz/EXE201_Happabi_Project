package com.minduc.happabi.service.notification;

import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;

import java.util.UUID;

public interface INotificationPublisher {
    default void publish(UUID targetUserId,
                         NotificationType type,
                         String title,
                         String message,
                         String resourceType,
                         String resourceId) {
        publish(targetUserId, null, type, title, message, resourceType, resourceId);
    }

    void publish(UUID targetUserId,
                 UserRole recipientRole,
                 NotificationType type,
                 String title,
                 String message,
                 String resourceType,
                 String resourceId);
}