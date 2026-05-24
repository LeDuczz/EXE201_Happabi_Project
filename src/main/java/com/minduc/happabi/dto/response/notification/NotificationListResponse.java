package com.minduc.happabi.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationListResponse {
    private long unreadCount;
    private List<NotificationResponse> notifications;
}
