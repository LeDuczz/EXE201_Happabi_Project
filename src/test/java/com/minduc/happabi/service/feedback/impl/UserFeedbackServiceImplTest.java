package com.minduc.happabi.service.feedback.impl;

import com.minduc.happabi.dto.request.feedback.CreateUserFeedbackRequest;
import com.minduc.happabi.dto.request.feedback.UpdateUserFeedbackStatusRequest;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserFeedback;
import com.minduc.happabi.enums.UserFeedbackCategory;
import com.minduc.happabi.enums.UserFeedbackStatus;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.UserFeedbackRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFeedbackServiceImplTest {

    @Mock
    private UserFeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAccountLookupService userAccountLookupService;

    @Mock
    private INotificationPublisher notificationPublisher;

    private UserFeedbackServiceImpl service;
    private User mother;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new UserFeedbackServiceImpl(
                feedbackRepository,
                userRepository,
                userAccountLookupService,
                notificationPublisher
        );
        mother = user("Mother Test", UserRole.MOTHER);
        admin = user("Admin Test", UserRole.ADMIN);
    }

    @Test
    void createMyFeedbackStoresFeedbackAndNotifiesAdmins() {
        CreateUserFeedbackRequest request = new CreateUserFeedbackRequest();
        request.setCategory(UserFeedbackCategory.APP_EXPERIENCE);
        request.setRating(5);
        request.setTitle("Improve booking page");
        request.setMessage("The booking page should be easier to scan.");

        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(userRepository.findActiveUsersByRoleName(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(invocation -> {
            UserFeedback feedback = invocation.getArgument(0);
            feedback.setId(UUID.randomUUID());
            return feedback;
        });

        var response = service.createMyFeedback(request);

        assertThat(response.getSubmittedByRole()).isEqualTo(UserRole.MOTHER);
        assertThat(response.getCategory()).isEqualTo(UserFeedbackCategory.APP_EXPERIENCE);
        assertThat(response.getStatus()).isEqualTo(UserFeedbackStatus.NEW);
        assertThat(response.getRating()).isEqualTo(5);
        verify(notificationPublisher).publish(eq(admin.getId()), any(), eq("New user feedback"), any(), eq("USER_FEEDBACK"), any());
        verify(notificationPublisher).publish(eq(mother.getId()), any(), eq("Feedback received"), any(), eq("USER_FEEDBACK"), any());
    }

    @Test
    void updateFeedbackStatusLocksFeedbackAndNotifiesSubmitter() {
        UUID feedbackId = UUID.randomUUID();
        UserFeedback feedback = UserFeedback.builder()
                .id(feedbackId)
                .submittedBy(mother)
                .submittedByRole(UserRole.MOTHER)
                .category(UserFeedbackCategory.SUGGESTION)
                .status(UserFeedbackStatus.NEW)
                .title("New idea")
                .message("Please add a better search filter.")
                .build();
        UpdateUserFeedbackStatusRequest request = new UpdateUserFeedbackStatusRequest();
        request.setStatus(UserFeedbackStatus.REVIEWING);
        request.setAdminNote("We are checking this.");

        when(feedbackRepository.findByIdForUpdate(feedbackId)).thenReturn(Optional.of(feedback));
        when(userAccountLookupService.getCurrentUser()).thenReturn(admin);
        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateFeedbackStatus(feedbackId, request);

        assertThat(response.getStatus()).isEqualTo(UserFeedbackStatus.REVIEWING);
        assertThat(response.getAdminNote()).isEqualTo("We are checking this.");
        assertThat(response.getReviewedByAdminName()).isEqualTo("Admin Test");
        verify(notificationPublisher).publish(eq(mother.getId()), any(), eq("Feedback status updated"), any(), eq("USER_FEEDBACK"), eq(feedbackId.toString()));
    }

    @Test
    void updateFeedbackStatusRejectsNewStatusFromAdmin() {
        UUID feedbackId = UUID.randomUUID();
        UserFeedback feedback = UserFeedback.builder()
                .id(feedbackId)
                .submittedBy(mother)
                .submittedByRole(UserRole.MOTHER)
                .category(UserFeedbackCategory.OTHER)
                .status(UserFeedbackStatus.REVIEWING)
                .title("Existing feedback")
                .message("Existing feedback message.")
                .build();
        UpdateUserFeedbackStatusRequest request = new UpdateUserFeedbackStatusRequest();
        request.setStatus(UserFeedbackStatus.NEW);

        when(feedbackRepository.findByIdForUpdate(feedbackId)).thenReturn(Optional.of(feedback));

        assertThatThrownBy(() -> service.updateFeedbackStatus(feedbackId, request))
                .isInstanceOf(AppException.class);
    }

    private User user(String fullName, UserRole roleName) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .isActive(true)
                .build();
        user.addRoleAssignment(Role.builder()
                .id(UUID.randomUUID())
                .roleName(roleName)
                .build());
        return user;
    }
}
