package com.minduc.happabi.service.notification.impl;

import com.minduc.happabi.dto.response.notification.NotificationListResponse;
import com.minduc.happabi.dto.response.notification.NotificationResponse;
import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.NotificationRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.notification.NotificationRealtimeDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String COGNITO_SUB = "notification-user-sub";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRealtimeDispatcher realtimeDispatcher;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                userRepository,
                realtimeDispatcher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsRecipientRoleAndDispatchesRealtime() {
        User user = userWithRoles(UserRole.NURSE);
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    notification.setId(notificationId);
                    return notification;
                });

        Notification saved = notificationService.create(
                user,
                UserRole.NURSE,
                NotificationType.PLATFORM_COMMISSION_UPDATED,
                "Commission changed",
                "New split applies to future bookings.",
                "SYSTEM_CONFIG",
                "platform.commission.rate");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecipientRole()).isEqualTo(UserRole.NURSE);
        assertThat(saved.getId()).isEqualTo(notificationId);
        verify(realtimeDispatcher).dispatch(notificationId);
    }

    @Test
    void getMyNotificationsReturnsOnlyRequestedActiveRole() {
        User user = userWithRoles(UserRole.MOTHER, UserRole.NURSE);
        Notification notification = notification(user, UserRole.NURSE);
        authenticate();
        when(userRepository.findByCognitoSub(COGNITO_SUB)).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserAndRecipientRoleAndReadAtIsNull(user, UserRole.NURSE)).thenReturn(2L);
        when(notificationRepository.findTop30ByUserAndRecipientRoleOrderByCreatedAtDesc(user, UserRole.NURSE))
                .thenReturn(List.of(notification));

        NotificationListResponse response = notificationService.getMyNotifications(UserRole.NURSE);

        assertThat(response.getUnreadCount()).isEqualTo(2L);
        assertThat(response.getNotifications()).singleElement()
                .extracting(NotificationResponse::getRecipientRole)
                .isEqualTo(UserRole.NURSE);
        verify(notificationRepository, never()).countByUserAndReadAtIsNull(user);
        verify(notificationRepository, never()).findTop30ByUserOrderByCreatedAtDesc(user);
    }

    @Test
    void getMyNotificationsUsesHighestAvailableRoleWhenActiveRoleIsMissing() {
        User user = userWithRoles(UserRole.MOTHER, UserRole.NURSE);
        authenticate();
        when(userRepository.findByCognitoSub(COGNITO_SUB)).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserAndRecipientRoleAndReadAtIsNull(user, UserRole.NURSE)).thenReturn(1L);
        when(notificationRepository.findTop30ByUserAndRecipientRoleOrderByCreatedAtDesc(user, UserRole.NURSE))
                .thenReturn(List.of(notification(user, UserRole.NURSE)));

        NotificationListResponse response = notificationService.getMyNotifications(null);

        assertThat(response.getUnreadCount()).isEqualTo(1L);
        assertThat(response.getNotifications()).singleElement()
                .extracting(NotificationResponse::getRecipientRole)
                .isEqualTo(UserRole.NURSE);
    }

    @Test
    void getMyNotificationsRejectsRoleThatDoesNotBelongToUser() {
        User user = userWithRoles(UserRole.MOTHER);
        authenticate();
        when(userRepository.findByCognitoSub(COGNITO_SUB)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> notificationService.getMyNotifications(UserRole.ADMIN))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Active role does not belong to current user");
    }


    @Test
    void markAsReadSetsReadTimestampWhenNotificationBelongsToCurrentUser() {
        User user = userWithRoles(UserRole.MOTHER);
        Notification notification = notification(user, UserRole.MOTHER);
        notification.setReadAt(null);
        authenticate();
        when(userRepository.findByCognitoSub(COGNITO_SUB)).thenReturn(Optional.of(user));
        when(notificationRepository.findByIdWithUser(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse response = notificationService.markAsRead(notification.getId());

        assertThat(response.getRead()).isTrue();
        assertThat(response.getRecipientRole()).isEqualTo(UserRole.MOTHER);
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadRejectsNotificationOwnedByAnotherUser() {
        User currentUser = userWithRoles(UserRole.MOTHER);
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Other User")
                .build();
        Notification notification = notification(otherUser, UserRole.MOTHER);
        authenticate();
        when(userRepository.findByCognitoSub(COGNITO_SUB)).thenReturn(Optional.of(currentUser));
        when(notificationRepository.findByIdWithUser(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(notification.getId()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Notification does not belong to current user");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", COGNITO_SUB)
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User userWithRoles(UserRole... roles) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Notification User")
                .cognitoSub(COGNITO_SUB)
                .build();
        for (UserRole role : roles) {
            user.addRoleAssignment(Role.builder().id(UUID.randomUUID()).roleName(role).build());
        }
        return user;
    }

    private Notification notification(User user, UserRole recipientRole) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .recipientRole(recipientRole)
                .type(NotificationType.WORK_SESSION_UPDATED)
                .title("Shift updated")
                .message("A shift status changed.")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}