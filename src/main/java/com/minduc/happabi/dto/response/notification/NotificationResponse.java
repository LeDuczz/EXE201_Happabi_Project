package com.minduc.happabi.dto.response.notification;

import com.minduc.happabi.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private String title;
    private String message;
    private String resourceType;
    private String resourceId;
    private Boolean read;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
}
