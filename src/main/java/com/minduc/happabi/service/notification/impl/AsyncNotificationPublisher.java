package com.minduc.happabi.service.notification.impl;

import com.minduc.happabi.dto.event.NotificationRequestedEvent;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.service.notification.INotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsyncNotificationPublisher implements INotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(UUID targetUserId,
                        NotificationType type,
                        String title,
                        String message,
                        String resourceType,
                        String resourceId) {
        if (targetUserId == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationRequestedEvent(
                targetUserId,
                type,
                title,
                message,
                resourceType,
                resourceId
        ));
    }
}
