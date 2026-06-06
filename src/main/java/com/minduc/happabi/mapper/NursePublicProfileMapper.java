package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseSkillResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NursePublicProfileMapper {

    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "featured", source = "profile.isFeatured")
    @Mapping(target = "certificationCount", expression = "java(certificationCount(certifications))")
    @Mapping(target = "certifications", source = "certifications")
    @Mapping(target = "skills", source = "skills")
    @Mapping(target = "eligibleServiceOfferings", source = "eligibleServiceOfferings")
    NursePublicProfileResponse toResponse(NurseProfile profile,
                                          List<NurseCertification> certifications,
                                          String avatarUrl,
                                          List<NurseSkillResponse> skills,
                                          List<ServiceOfferingResponse> eligibleServiceOfferings);

    NursePublicCertificationResponse toCertificationResponse(NurseCertification certification);

    default Long certificationCount(List<NurseCertification> certifications) {
        return certifications == null ? 0L : (long) certifications.size();
    }
}
