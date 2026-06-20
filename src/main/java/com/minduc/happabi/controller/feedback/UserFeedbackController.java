package com.minduc.happabi.controller.feedback;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.feedback.CreateUserFeedbackRequest;
import com.minduc.happabi.dto.response.feedback.UserFeedbackResponse;
import com.minduc.happabi.service.feedback.IUserFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedbacks/me")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MOTHER','NURSE','DOCTOR')")
public class UserFeedbackController {

    private final IUserFeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<BaseResponse<UserFeedbackResponse>> createMyFeedback(
            @Valid @RequestBody CreateUserFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created("Feedback submitted successfully.",
                        feedbackService.createMyFeedback(request)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<Page<UserFeedbackResponse>>> getMyFeedbacks(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get my feedback successfully.",
                feedbackService.getMyFeedbacks(pageable)));
    }
}
