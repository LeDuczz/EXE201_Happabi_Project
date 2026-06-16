package com.minduc.happabi.controller.review;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.review.CreateNurseReviewRequest;
import com.minduc.happabi.dto.response.review.NurseReviewResponse;
import com.minduc.happabi.service.review.INurseReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mothers/me/work-sessions/{workSessionId}/review")
@RequiredArgsConstructor
@Tag(name = "Mother Nurse Reviews", description = "APIs for mothers to review completed nurse work sessions")
@SecurityRequirement(name = "bearerAuth")
public class MotherNurseReviewController {

    private final INurseReviewService nurseReviewService;

    @GetMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<NurseReviewResponse>> getMyReview(
            @PathVariable UUID workSessionId) {
        return ResponseEntity.ok(BaseResponse.ok(nurseReviewService.getMyReview(workSessionId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<NurseReviewResponse>> createMyReview(
            @PathVariable UUID workSessionId,
            @Valid @RequestBody CreateNurseReviewRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Nurse review submitted.",
                nurseReviewService.createMyReview(workSessionId, request)));
    }
}
