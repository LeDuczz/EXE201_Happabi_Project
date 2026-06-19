package com.minduc.happabi.dto.response.mother;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseAiComparisonResponse {

    private List<NurseComparisonCandidateResponse> candidates;
    private UUID suggestedProfileId;
    private String suggestedNurseName;
    private String summary;
    private String modelUsed;
    private String resolutionSource;
    private Boolean aiGenerated;
}
