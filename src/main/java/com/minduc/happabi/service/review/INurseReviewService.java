package com.minduc.happabi.service.review;

import com.minduc.happabi.dto.request.review.CreateNurseReviewRequest;
import com.minduc.happabi.dto.response.review.NurseReviewResponse;

import java.util.UUID;

public interface INurseReviewService {
    NurseReviewResponse createMyReview(UUID workSessionId, CreateNurseReviewRequest request);

    NurseReviewResponse getMyReview(UUID workSessionId);
}
