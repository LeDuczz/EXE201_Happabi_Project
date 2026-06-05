package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.nurse.NursePublicCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
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
    NursePublicProfileResponse toResponse(NurseProfile profile,
                                          List<NurseCertification> certifications,
                                          String avatarUrl);

    NursePublicCertificationResponse toCertificationResponse(NurseCertification certification);

    default Long certificationCount(List<NurseCertification> certifications) {
        return certifications == null ? 0L : (long) certifications.size();
    }
}
