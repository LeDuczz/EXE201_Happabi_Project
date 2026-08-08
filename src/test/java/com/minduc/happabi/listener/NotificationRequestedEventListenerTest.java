package com.minduc.happabi.listener;

import com.minduc.happabi.dto.event.NotificationRequestedEvent;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.notification.INotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRequestedEventListenerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private INotificationService notificationService;

    private NotificationRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationRequestedEventListener(userRepository, notificationService);
    }

    @Test
    void onNotificationRequestedCreatesNotificationWithRecipientRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).fullName("Admin User").build();
        NotificationRequestedEvent event = event(userId, UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        listener.onNotificationRequested(event);

        verify(notificationService).create(
                user,
                UserRole.ADMIN,
                NotificationType.WORK_SESSION_UPDATED,
                "Title",
                "Message",
                "USER_FEEDBACK",
                "feedback-1");
    }

    @Test
    void onNotificationRequestedSkipsMissingUser() {
        UUID userId = UUID.randomUUID();
        NotificationRequestedEvent event = event(userId, UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        listener.onNotificationRequested(event);

        verify(notificationService, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private NotificationRequestedEvent event(UUID userId, UserRole recipientRole) {
        return new NotificationRequestedEvent(
                userId,
                recipientRole,
                NotificationType.WORK_SESSION_UPDATED,
                "Title",
                "Message",
                "USER_FEEDBACK",
                "feedback-1");
    }
}