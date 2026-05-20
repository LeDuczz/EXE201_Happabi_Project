package com.minduc.happabi.listener;

import com.minduc.happabi.dto.response.notification.RealtimeNotificationPayload;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.dto.event.NotificationCreatedEvent;
import com.minduc.happabi.repository.NotificationRepository;
import com.minduc.happabi.service.notification.RealtimeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimeEventListener {

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<RealtimeNotificationService> realtimeNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Notification notification = notificationRepository.findByIdWithUser(event.notificationId())
                .orElse(null);
        if (notification == null) {
            return;
        }

        long unreadCount = notificationRepository.countByUserAndReadAtIsNull(notification.getUser());
        RealtimeNotificationPayload payload = RealtimeNotificationPayload.builder()
                .notificationId(notification.getId())
                .targetUserId(notification.getUser().getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .resourceType(notification.getResourceType())
                .resourceId(notification.getResourceId())
                .unreadCount(unreadCount)
                .createdAt(notification.getCreatedAt())
                .build();

        RealtimeNotificationService realtime = realtimeNotificationService.getIfAvailable();
        if (realtime == null) {
            log.debug("[Notification] Realtime socket is disabled. notificationId={}", notification.getId());
            return;
        }

        realtime.pushToUser(notification.getUser().getId(), payload);
        log.info("[Notification] Realtime notification pushed: notificationId={} userId={}",
                notification.getId(), notification.getUser().getId());
    }
}
