package com.minduc.happabi.integration.sqs;

import java.util.UUID;

public interface INurseReviewAggregationPublisher {
    void publishRecalculate(UUID nurseProfileId, UUID reviewId, String reason);
}
