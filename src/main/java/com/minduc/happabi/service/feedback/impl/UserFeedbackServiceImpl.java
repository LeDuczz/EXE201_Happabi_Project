package com.minduc.happabi.service.feedback.impl;

import com.minduc.happabi.dto.request.feedback.CreateUserFeedbackRequest;
import com.minduc.happabi.dto.request.feedback.UpdateUserFeedbackStatusRequest;
import com.minduc.happabi.dto.response.feedback.UserFeedbackResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserFeedback;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserFeedbackStatus;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserFeedbackErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.UserFeedbackRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.feedback.IUserFeedbackService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFeedbackServiceImpl implements IUserFeedbackService {

    private final UserFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final INotificationPublisher notificationPublisher;

    @Override
    @LogExecution
    @TimedAction("CREATE_USER_FEEDBACK")
    @AuditAction(action = "CREATE_USER_FEEDBACK", resourceType = "USER_FEEDBACK")
    @Transactional
    @PreAuthorize("hasAnyRole('MOTHER','NURSE','DOCTOR')")
    public UserFeedbackResponse createMyFeedback(CreateUserFeedbackRequest request) {
        User currentUser = userAccountLookupService.getCurrentUser();
        UserRole submittedByRole = resolveSubmitterRole(currentUser);

        UserFeedback feedback = feedbackRepository.save(UserFeedback.builder()
                .submittedBy(currentUser)
                .submittedByRole(submittedByRole)
                .category(request.getCategory())
                .rating(request.getRating())
                .title(cleanRequired(request.getTitle()))
                .message(cleanRequired(request.getMessage()))
                .status(UserFeedbackStatus.NEW)
                .build());

        notifyAdmins(feedback);
        notifySubmitter(feedback,
                "Feedback received",
                "Your feedback has been received. Our team will review it and use it to improve Happabi.");
        log.info("[UserFeedback] Created feedbackId={} userId={} role={} category={}",
                feedback.getId(), currentUser.getId(), submittedByRole, feedback.getCategory());
        return toResponse(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MOTHER','NURSE','DOCTOR')")
    public Page<UserFeedbackResponse> getMyFeedbacks(Pageable pageable) {
        User currentUser = userAccountLookupService.getCurrentUser();
        return feedbackRepository.findBySubmittedBy_IdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserFeedbackResponse> getFeedbacks(UserFeedbackStatus status, Pageable pageable) {
        Page<UserFeedback> page = status == null
                ? feedbackRepository.findAllByOrderByCreatedAtDesc(pageable)
                : feedbackRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @LogExecution
    @TimedAction("UPDATE_USER_FEEDBACK_STATUS")
    @AuditAction(action = "UPDATE_USER_FEEDBACK_STATUS", resourceType = "USER_FEEDBACK")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserFeedbackResponse updateFeedbackStatus(UUID feedbackId, UpdateUserFeedbackStatusRequest request) {
        UserFeedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new AppException(UserFeedbackErrorCode.USER_FEEDBACK_NOT_FOUND));
        UserFeedbackStatus nextStatus = request.getStatus();
        if (nextStatus == UserFeedbackStatus.NEW) {
            throw new AppException(UserFeedbackErrorCode.USER_FEEDBACK_INVALID_STATUS);
        }

        User admin = userAccountLookupService.getCurrentUser();
        feedback.setStatus(nextStatus);
        feedback.setReviewedByAdmin(admin);
        feedback.setReviewedAt(OffsetDateTime.now());
        feedback.setAdminNote(cleanOptional(request.getAdminNote()));
        UserFeedback saved = feedbackRepository.save(feedback);

        notifySubmitter(saved,
                "Feedback status updated",
                "Your feedback status was updated to " + saved.getStatus().name() + ".");
        log.info("[UserFeedback] Updated feedbackId={} status={} adminId={}",
                saved.getId(), saved.getStatus(), admin.getId());
        return toResponse(saved);
    }

    private UserRole resolveSubmitterRole(User user) {
        if (user.hasRole(UserRole.MOTHER)) {
            return UserRole.MOTHER;
        }
        if (user.hasRole(UserRole.NURSE)) {
            return UserRole.NURSE;
        }
        if (user.hasRole(UserRole.DOCTOR)) {
            return UserRole.DOCTOR;
        }
        throw new AppException(UserFeedbackErrorCode.USER_FEEDBACK_ROLE_NOT_ALLOWED);
    }

    private void notifyAdmins(UserFeedback feedback) {
        userRepository.findActiveUsersByRoleName(UserRole.ADMIN).forEach(admin ->
                notificationPublisher.publish(
                        admin.getId(),
                        NotificationType.WORK_SESSION_UPDATED,
                        "New user feedback",
                        "%s submitted feedback about %s."
                                .formatted(feedback.getSubmittedBy().getFullName(), feedback.getCategory().name()),
                        "USER_FEEDBACK",
                        feedback.getId().toString()));
    }

    private void notifySubmitter(UserFeedback feedback, String title, String message) {
        notificationPublisher.publish(
                feedback.getSubmittedBy().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "USER_FEEDBACK",
                feedback.getId().toString());
    }

    private UserFeedbackResponse toResponse(UserFeedback feedback) {
        return UserFeedbackResponse.builder()
                .id(feedback.getId())
                .submittedByUserId(feedback.getSubmittedBy().getId())
                .submittedByName(feedback.getSubmittedBy().getFullName())
                .submittedByRole(feedback.getSubmittedByRole())
                .category(feedback.getCategory())
                .status(feedback.getStatus())
                .rating(feedback.getRating())
                .title(feedback.getTitle())
                .message(feedback.getMessage())
                .adminNote(feedback.getAdminNote())
                .reviewedByAdminName(feedback.getReviewedByAdmin() == null
                        ? null
                        : feedback.getReviewedByAdmin().getFullName())
                .reviewedAt(feedback.getReviewedAt())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }

    private String cleanRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
