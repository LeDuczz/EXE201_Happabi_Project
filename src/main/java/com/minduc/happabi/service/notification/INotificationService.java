package com.minduc.happabi.service.notification;

import com.minduc.happabi.dto.response.notification.NotificationListResponse;
import com.minduc.happabi.dto.response.notification.NotificationResponse;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;

import java.util.UUID;

public interface INotificationService {

    Notification create(User targetUser, UserRole recipientRole, NotificationType type, String title, String message,
                        String resourceType, String resourceId);

    NotificationListResponse getMyNotifications(UserRole activeRole);

    NotificationResponse markAsRead(UUID notificationId);

}