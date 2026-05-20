package com.minduc.happabi.service.notification;

import com.minduc.happabi.dto.response.notification.NotificationListResponse;
import com.minduc.happabi.dto.response.notification.NotificationResponse;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;

import java.util.UUID;

public interface NotificationService {

    Notification create(User targetUser, NotificationType type, String title, String message,
                        String resourceType, String resourceId);

    NotificationListResponse getMyNotifications();

    NotificationResponse markAsRead(UUID notificationId);
}
