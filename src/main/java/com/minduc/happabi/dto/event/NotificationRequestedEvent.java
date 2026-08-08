package com.minduc.happabi.dto.event;

import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;

import java.util.UUID;

public record NotificationRequestedEvent(
        UUID targetUserId,
        UserRole recipientRole,
        NotificationType type,
        String title,
        String message,
        String resourceType,
        String resourceId
) {
}