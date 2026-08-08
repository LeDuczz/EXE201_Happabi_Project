package com.minduc.happabi.service.notification.impl;

import com.minduc.happabi.dto.event.NotificationRequestedEvent;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AsyncNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AsyncNotificationPublisher(eventPublisher);
    }

    @Test
    void publishIncludesRecipientRoleInEvent() {
        UUID userId = UUID.randomUUID();

        publisher.publish(
                userId,
                UserRole.ADMIN,
                NotificationType.WORK_SESSION_UPDATED,
                "Title",
                "Message",
                "USER_FEEDBACK",
                "feedback-1");

        ArgumentCaptor<NotificationRequestedEvent> captor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationRequestedEvent event = captor.getValue();
        assertThat(event.targetUserId()).isEqualTo(userId);
        assertThat(event.recipientRole()).isEqualTo(UserRole.ADMIN);
        assertThat(event.type()).isEqualTo(NotificationType.WORK_SESSION_UPDATED);
        assertThat(event.resourceId()).isEqualTo("feedback-1");
    }

    @Test
    void publishSkipsNullTargetUser() {
        publisher.publish(
                null,
                UserRole.NURSE,
                NotificationType.NURSE_SUSPENDED,
                "Title",
                "Message",
                "NURSE",
                "nurse-1");

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}