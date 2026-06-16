package com.minduc.happabi.dto.event;

import com.minduc.happabi.enums.NotificationType;

import java.util.UUID;

public record NotificationRequestedEvent(
        UUID targetUserId,
        NotificationType type,
        String title,
        String message,
        String resourceType,
        String resourceId
) {
}
