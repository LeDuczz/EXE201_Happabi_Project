package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.mother.NurseComparisonCandidateResponse;
import com.minduc.happabi.entity.NurseProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NurseComparisonMapper {

    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "featured", source = "profile.isFeatured")
    @Mapping(target = "verifiedCertifications", source = "verifiedCertificationNames")
    @Mapping(target = "fitScore", source = "fitScore")
    @Mapping(target = "strengths", source = "strengths")
    @Mapping(target = "watchPoints", source = "watchPoints")
    NurseComparisonCandidateResponse toCandidateResponse(NurseProfile profile,
                                                        List<String> verifiedCertificationNames,
                                                        Integer fitScore,
                                                        List<String> strengths,
                                                        List<String> watchPoints);
}
