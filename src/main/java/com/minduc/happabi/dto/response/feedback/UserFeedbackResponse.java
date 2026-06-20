package com.minduc.happabi.dto.response.feedback;

import com.minduc.happabi.enums.UserFeedbackCategory;
import com.minduc.happabi.enums.UserFeedbackStatus;
import com.minduc.happabi.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserFeedbackResponse {

    private UUID id;
    private UUID submittedByUserId;
    private String submittedByName;
    private UserRole submittedByRole;
    private UserFeedbackCategory category;
    private UserFeedbackStatus status;
    private Integer rating;
    private String title;
    private String message;
    private String adminNote;
    private String reviewedByAdminName;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
