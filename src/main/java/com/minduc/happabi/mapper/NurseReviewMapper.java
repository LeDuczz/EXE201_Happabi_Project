package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.review.NurseReviewResponse;
import com.minduc.happabi.entity.NurseReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NurseReviewMapper {

    @Mapping(target = "workSessionId", source = "workSession.id")
    @Mapping(target = "nurseProfileId", source = "nurseProfile.id")
    @Mapping(target = "nurseName", source = "nurseProfile.user.fullName")
    NurseReviewResponse toResponse(NurseReview review);
}
