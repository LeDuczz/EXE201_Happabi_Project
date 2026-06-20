package com.minduc.happabi.dto.request.feedback;

import com.minduc.happabi.enums.UserFeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserFeedbackStatusRequest {

    @NotNull
    private UserFeedbackStatus status;

    @Size(max = 500)
    private String adminNote;
}
