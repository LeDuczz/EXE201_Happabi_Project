package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.feedback.UpdateUserFeedbackStatusRequest;
import com.minduc.happabi.dto.response.feedback.UserFeedbackResponse;
import com.minduc.happabi.enums.UserFeedbackStatus;
import com.minduc.happabi.service.feedback.IUserFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/feedbacks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserFeedbackController {

    private final IUserFeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<BaseResponse<Page<UserFeedbackResponse>>> getFeedbacks(
            @RequestParam(required = false) UserFeedbackStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get user feedback successfully.",
                feedbackService.getFeedbacks(status, pageable)));
    }

    @PatchMapping("/{feedbackId}/status")
    public ResponseEntity<BaseResponse<UserFeedbackResponse>> updateFeedbackStatus(
            @PathVariable UUID feedbackId,
            @Valid @RequestBody UpdateUserFeedbackStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Feedback status updated.",
                feedbackService.updateFeedbackStatus(feedbackId, request)));
    }
}
