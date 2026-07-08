package com.minduc.happabi.listener;

import com.minduc.happabi.dto.event.NotificationRequestedEvent;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.notification.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestedEventListener {

    private final UserRepository userRepository;
    private final INotificationService notificationService;

    @Async("appTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        try {
            User targetUser = userRepository.findById(event.targetUserId()).orElse(null);
            if (targetUser == null) {
                log.warn("[Notification] Skip notification because target user was not found: userId={}",
                        event.targetUserId());
                return;
            }
            notificationService.create(targetUser, event.type(), event.title(), event.message(),
                    event.resourceType(), event.resourceId());
        } catch (RuntimeException e) {
            log.warn("[Notification] Failed to create async notification: userId={} resourceType={} resourceId={}",
                    event.targetUserId(), event.resourceType(), event.resourceId(), e);
        }
    }
}
