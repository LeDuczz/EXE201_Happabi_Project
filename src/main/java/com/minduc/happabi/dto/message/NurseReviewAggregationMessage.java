package com.minduc.happabi.dto.message;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NurseReviewAggregationMessage(
        String type,
        UUID nurseProfileId,
        UUID reviewId,
        String reason,
        OffsetDateTime requestedAt
) {
    public static final String TYPE = "NURSE_REVIEW_AGGREGATION_REQUESTED";
}
