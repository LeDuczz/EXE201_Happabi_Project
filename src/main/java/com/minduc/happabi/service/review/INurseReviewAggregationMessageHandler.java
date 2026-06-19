package com.minduc.happabi.service.review;

import com.minduc.happabi.dto.message.NurseReviewAggregationMessage;

public interface INurseReviewAggregationMessageHandler {
    boolean supports(NurseReviewAggregationMessage message);

    void handle(NurseReviewAggregationMessage message);
}
