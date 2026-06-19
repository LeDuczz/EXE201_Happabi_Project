package com.minduc.happabi.service.review;

import java.util.UUID;

public interface INurseRatingAggregationService {
    void recalculate(UUID nurseProfileId);
}
