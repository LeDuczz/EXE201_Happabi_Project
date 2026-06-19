package com.minduc.happabi.service.notification;

import com.minduc.happabi.enums.NotificationType;

import java.util.UUID;

public interface INotificationPublisher {
    void publish(UUID targetUserId,
                 NotificationType type,
                 String title,
                 String message,
                 String resourceType,
                 String resourceId);
}
