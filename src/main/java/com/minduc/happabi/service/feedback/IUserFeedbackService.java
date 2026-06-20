package com.minduc.happabi.service.feedback;

import com.minduc.happabi.dto.request.feedback.CreateUserFeedbackRequest;
import com.minduc.happabi.dto.request.feedback.UpdateUserFeedbackStatusRequest;
import com.minduc.happabi.dto.response.feedback.UserFeedbackResponse;
import com.minduc.happabi.enums.UserFeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IUserFeedbackService {

    UserFeedbackResponse createMyFeedback(CreateUserFeedbackRequest request);

    Page<UserFeedbackResponse> getMyFeedbacks(Pageable pageable);

    Page<UserFeedbackResponse> getFeedbacks(UserFeedbackStatus status, Pageable pageable);

    UserFeedbackResponse updateFeedbackStatus(UUID feedbackId, UpdateUserFeedbackStatusRequest request);
}
