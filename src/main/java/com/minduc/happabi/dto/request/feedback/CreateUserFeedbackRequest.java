package com.minduc.happabi.dto.request.feedback;

import com.minduc.happabi.enums.UserFeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserFeedbackRequest {

    @NotNull
    private UserFeedbackCategory category;

    @Min(1)
    @Max(5)
    private Integer rating;

    @NotBlank
    @Size(min = 5, max = 120)
    private String title;

    @NotBlank
    @Size(min = 10, max = 2000)
    private String message;
}
