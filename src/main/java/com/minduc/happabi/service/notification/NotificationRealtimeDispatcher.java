package com.minduc.happabi.service.notification;

import com.minduc.happabi.dto.response.notification.RealtimeNotificationPayload;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRealtimeDispatcher {

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<RealtimeNotificationService> realtimeNotificationService;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void dispatch(UUID notificationId) {
        Notification notification = notificationRepository.findByIdWithUser(notificationId)
                .orElse(null);
        if (notification == null) {
            log.warn("[Notification] Skip realtime push because notification was not found: notificationId={}",
                    notificationId);
            return;
        }

        RealtimeNotificationService realtime = realtimeNotificationService.getIfAvailable();
        if (realtime == null) {
            log.warn("[Notification] Realtime socket is disabled. notificationId={}", notificationId);
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
                .createdAt(notification.getCreatedAt().toString())
                .build();

        realtime.pushToUser(notification.getUser().getId(), payload);
        log.info("[Notification] Realtime notification pushed: notificationId={} userId={}",
                notification.getId(), notification.getUser().getId());
    }
}
