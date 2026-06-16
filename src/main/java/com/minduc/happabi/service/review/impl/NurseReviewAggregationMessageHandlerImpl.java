package com.minduc.happabi.service.review.impl;

import com.minduc.happabi.dto.message.NurseReviewAggregationMessage;
import com.minduc.happabi.service.review.INurseRatingAggregationService;
import com.minduc.happabi.service.review.INurseReviewAggregationMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NurseReviewAggregationMessageHandlerImpl implements INurseReviewAggregationMessageHandler {

    private final INurseRatingAggregationService ratingAggregationService;

    @Override
    public boolean supports(NurseReviewAggregationMessage message) {
        return message != null && NurseReviewAggregationMessage.TYPE.equals(message.type());
    }

    @Override
    public void handle(NurseReviewAggregationMessage message) {
        ratingAggregationService.recalculate(message.nurseProfileId());
    }
}
