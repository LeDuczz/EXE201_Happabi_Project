package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.nurse.NurseCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NurseContractResponse;
import com.minduc.happabi.dto.response.nurse.NurseKycResponse;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseContractStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NurseOnboardingMapper {

    @Mapping(target = "profileId", source = "profile.id")
    @Mapping(target = "userId", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "phone", source = "profile.user.phone")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "licenseNumber", source = "profile.licenseNumber")
    @Mapping(target = "dateOfBirth", source = "profile.dateOfBirth")
    @Mapping(target = "specialty", source = "profile.specialty")
    @Mapping(target = "experienceYears", source = "profile.experienceYears")
    @Mapping(target = "bio", source = "profile.bio")
    @Mapping(target = "serviceArea", source = "profile.serviceArea")
    @Mapping(target = "address", source = "profile.address")
    @Mapping(target = "city", source = "profile.city")
    @Mapping(target = "nurseStatus", source = "profile.nurseStatus")
    @Mapping(target = "rejectionReason", source = "profile.rejectionReason")
    @Mapping(target = "lastStatusChangedAt", source = "profile.lastStatusChangedAt")
    @Mapping(target = "profileCompleted", expression = "java(isProfileCompleted(profile))")
    @Mapping(target = "kycCompleted", expression = "java(isKycCompleted(kyc))")
    @Mapping(target = "certificationsCompleted", expression = "java(certifications != null && !certifications.isEmpty())")
    @Mapping(target = "contractSigned", expression = "java(isContractSigned(latestContract))")
    @Mapping(target = "kyc", source = "kyc")
    @Mapping(target = "certifications", source = "certifications")
    @Mapping(target = "latestContract", source = "latestContract")
    NurseOnboardingResponse toResponse(NurseProfile profile,
                                       NurseKyc kyc,
                                       List<NurseCertification> certifications,
                                       NurseContract latestContract);

    @Mapping(target = "cccdNumberMasked", expression = "java(maskId(kyc.getCccdNumber()))")
    @Mapping(target = "hasFrontImage", expression = "java(hasText(kyc.getCccdFrontS3Key()))")
    @Mapping(target = "hasBackImage", expression = "java(hasText(kyc.getCccdBackS3Key()))")
    NurseKycResponse toKycResponse(NurseKyc kyc);

    @Mapping(target = "hasDocument", expression = "java(hasText(certification.getDocumentS3Key()))")
    @Mapping(target = "verified", source = "isVerified")
    NurseCertificationResponse toCertificationResponse(NurseCertification certification);

    NurseContractResponse toContractResponse(NurseContract contract);

    default boolean isProfileCompleted(NurseProfile profile) {
        return profile != null
                && hasText(profile.getLicenseNumber())
                && profile.getDateOfBirth() != null
                && profile.getSpecialty() != null
                && profile.getExperienceYears() != null
                && hasText(profile.getAddress())
                && hasText(profile.getCity());
    }

    default boolean isKycCompleted(NurseKyc kyc) {
        return kyc != null
                && hasText(kyc.getCccdNumber())
                && hasText(kyc.getCccdName())
                && hasText(kyc.getCccdFrontS3Key())
                && hasText(kyc.getCccdBackS3Key());
    }

    default boolean isContractSigned(NurseContract contract) {
        return contract != null && contract.getStatus() == NurseContractStatus.SIGNED;
    }

    default boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    default String maskId(String value) {
        if (value == null || value.length() < 6) {
            return "***";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }
}
