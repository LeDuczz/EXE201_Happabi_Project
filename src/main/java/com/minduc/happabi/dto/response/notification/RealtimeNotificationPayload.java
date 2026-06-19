package com.minduc.happabi.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeNotificationPayload {
    private UUID notificationId;
    private UUID targetUserId;
    private NotificationType type;
    private String title;
    private String message;
    private String resourceType;
    private String resourceId;
    private long unreadCount;
    private String createdAt;
}
