package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.EkycStatus;
import com.minduc.happabi.enums.NurseContractStatus;
import com.minduc.happabi.enums.NurseStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring", uses = NurseOnboardingMapper.class)
public interface NurseProfileMapper {

    @Mapping(target = "id", source = "profile.user.id")
    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "phone", source = "profile.user.phone")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "dayOfBirth", expression = "java(toDateString(profile.getDateOfBirth()))")
    @Mapping(target = "licenseNumber", source = "profile.licenseNumber")
    @Mapping(target = "specialty", source = "profile.specialty")
    @Mapping(target = "experienceYears", source = "profile.experienceYears")
    @Mapping(target = "bio", source = "profile.bio")
    @Mapping(target = "serviceArea", source = "profile.serviceArea")
    @Mapping(target = "address", source = "profile.address")
    @Mapping(target = "city", source = "profile.city")
    @Mapping(target = "nurseStatus", source = "profile.nurseStatus")
    @Mapping(target = "availabilityStatus", source = "profile.availabilityStatus")
    @Mapping(target = "ratingAvg", source = "profile.ratingAvg")
    @Mapping(target = "totalReviews", source = "profile.totalReviews")
    @Mapping(target = "totalCompletedJobs", source = "profile.totalCompletedJobs")
    @Mapping(target = "responseRate", source = "profile.responseRate")
    @Mapping(target = "backgroundChecked", source = "profile.backgroundChecked")
    @Mapping(target = "featured", source = "profile.isFeatured")
    @Mapping(target = "kycStatus", source = "kyc.ekycStatus")
    @Mapping(target = "kycVerified", expression = "java(isKycVerified(kyc))")
    @Mapping(target = "kycHasFrontImage", expression = "java(hasText(kyc == null ? null : kyc.getCccdFrontS3Key()))")
    @Mapping(target = "kycHasBackImage", expression = "java(hasText(kyc == null ? null : kyc.getCccdBackS3Key()))")
    @Mapping(target = "contractStatus", source = "latestContract.status")
    @Mapping(target = "contractSigned", expression = "java(isContractSigned(latestContract))")
    @Mapping(target = "contractSignedAt", source = "latestContract.signedAt")
    @Mapping(target = "profileCompleted", expression = "java(isProfileCompleted(profile))")
    @Mapping(target = "certificationsCompleted", expression = "java(certifications != null && !certifications.isEmpty())")
    @Mapping(target = "certificationCount", expression = "java(certificationCount(certifications))")
    @Mapping(target = "certifications", source = "certifications")
    @Mapping(target = "canEditProfessionalInfo", expression = "java(canEditProfessionalInfo(profile))")
    NurseProfileResponse toResponse(NurseProfile profile,
                                    NurseKyc kyc,
                                    List<NurseCertification> certifications,
                                    NurseContract latestContract,
                                    String avatarUrl);

    default boolean isProfileCompleted(NurseProfile profile) {
        return profile != null
                && hasText(profile.getLicenseNumber())
                && profile.getDateOfBirth() != null
                && profile.getSpecialty() != null
                && profile.getExperienceYears() != null
                && hasText(profile.getAddress())
                && hasText(profile.getCity());
    }

    default boolean isKycVerified(NurseKyc kyc) {
        return kyc != null && kyc.getEkycStatus() == EkycStatus.PASSED;
    }

    default boolean isContractSigned(NurseContract contract) {
        return contract != null && contract.getStatus() == NurseContractStatus.SIGNED;
    }

    default boolean canEditProfessionalInfo(NurseProfile profile) {
        return profile != null
                && (profile.getNurseStatus() == NurseStatus.PENDING_SUBMIT
                || profile.getNurseStatus() == NurseStatus.REJECTED);
    }

    default Long certificationCount(List<NurseCertification> certifications) {
        return certifications == null ? 0L : (long) certifications.size();
    }

    default boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    default String toDateString(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
