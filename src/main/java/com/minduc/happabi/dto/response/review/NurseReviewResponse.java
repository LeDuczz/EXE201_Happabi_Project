package com.minduc.happabi.dto.response.review;

import com.minduc.happabi.enums.NurseReviewTag;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NurseReviewResponse {
    private UUID id;
    private UUID workSessionId;
    private UUID nurseProfileId;
    private String nurseName;
    private Integer rating;
    private String comment;
    private List<NurseReviewTag> tags;
    private OffsetDateTime createdAt;
}
