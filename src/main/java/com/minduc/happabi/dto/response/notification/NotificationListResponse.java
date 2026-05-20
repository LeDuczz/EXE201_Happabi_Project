package com.minduc.happabi.dto.response.notification;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationListResponse {
    private long unreadCount;
    private List<NotificationResponse> notifications;
}
