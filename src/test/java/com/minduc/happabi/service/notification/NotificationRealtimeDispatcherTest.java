package com.minduc.happabi.service.notification;

import com.minduc.happabi.dto.response.notification.RealtimeNotificationPayload;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ObjectProvider<RealtimeNotificationService> realtimeNotificationServiceProvider;

    @Mock
    private RealtimeNotificationService realtimeNotificationService;

    private NotificationRealtimeDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationRealtimeDispatcher(notificationRepository, realtimeNotificationServiceProvider);
    }

    @Test
    void dispatchPushesRoleScopedUnreadCountAndPayload() {
        User user = User.builder().id(UUID.randomUUID()).fullName("Nurse User").build();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .recipientRole(UserRole.NURSE)
                .type(NotificationType.NURSE_BOOKING_ASSIGNED)
                .title("New booking")
                .message("You have a new booking.")
                .resourceType("BOOKING")
                .resourceId("booking-1")
                .createdAt(OffsetDateTime.parse("2026-08-08T10:00:00+07:00"))
                .build();

        when(notificationRepository.findByIdWithUser(notification.getId())).thenReturn(Optional.of(notification));
        when(realtimeNotificationServiceProvider.getIfAvailable()).thenReturn(realtimeNotificationService);
        when(notificationRepository.countByUserAndRecipientRoleAndReadAtIsNull(user, UserRole.NURSE)).thenReturn(3L);

        dispatcher.dispatch(notification.getId());

        ArgumentCaptor<RealtimeNotificationPayload> payloadCaptor = ArgumentCaptor.forClass(RealtimeNotificationPayload.class);
        verify(realtimeNotificationService).pushToUser(eq(user.getId()), payloadCaptor.capture());
        RealtimeNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.getRecipientRole()).isEqualTo(UserRole.NURSE);
        assertThat(payload.getUnreadCount()).isEqualTo(3L);
        assertThat(payload.getNotificationId()).isEqualTo(notification.getId());
        verify(notificationRepository, never()).countByUserAndReadAtIsNull(user);
    }

    @Test
    void dispatchSkipsWhenRealtimeServiceIsUnavailable() {
        User user = User.builder().id(UUID.randomUUID()).fullName("Nurse User").build();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .recipientRole(UserRole.NURSE)
                .type(NotificationType.NURSE_BOOKING_ASSIGNED)
                .title("New booking")
                .message("You have a new booking.")
                .createdAt(OffsetDateTime.now())
                .build();

        when(notificationRepository.findByIdWithUser(notification.getId())).thenReturn(Optional.of(notification));
        when(realtimeNotificationServiceProvider.getIfAvailable()).thenReturn(null);

        dispatcher.dispatch(notification.getId());

        verify(notificationRepository, never()).countByUserAndRecipientRoleAndReadAtIsNull(user, UserRole.NURSE);
        verify(realtimeNotificationService, never()).pushToUser(user.getId(), null);
    }
}