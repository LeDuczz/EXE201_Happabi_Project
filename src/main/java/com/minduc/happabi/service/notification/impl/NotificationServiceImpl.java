package com.minduc.happabi.service.notification.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.response.notification.NotificationListResponse;
import com.minduc.happabi.dto.response.notification.NotificationResponse;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.dto.event.NotificationCreatedEvent;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NotificationRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    @LogExecution
    @TimedAction("CREATE_NOTIFICATION")
    public Notification create(User targetUser, NotificationType type, String title, String message,
                               String resourceType, String resourceId) {
        Notification notification = Notification.builder()
                .user(targetUser)
                .type(type)
                .title(title)
                .message(message)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .build();

        Notification saved = notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("GET_MY_NOTIFICATIONS")
    public NotificationListResponse getMyNotifications() {
        User user = currentUser();
        return NotificationListResponse.builder()
                .unreadCount(notificationRepository.countByUserAndReadAtIsNull(user))
                .notifications(notificationRepository.findTop30ByUserOrderByCreatedAtDesc(user).stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("MARK_NOTIFICATION_AS_READ")
    public NotificationResponse markAsRead(UUID notificationId) {
        User user = currentUser();
        Notification notification = notificationRepository.findByIdWithUser(notificationId)
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Notification not found."));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Notification does not belong to current user.");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    private User currentUser() {
        String cognitoSub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        return userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .resourceType(notification.getResourceType())
                .resourceId(notification.getResourceId())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
