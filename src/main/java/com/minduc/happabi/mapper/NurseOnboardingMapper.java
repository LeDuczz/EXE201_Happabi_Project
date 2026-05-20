package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.nurse.*;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseContractStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NurseOnboardingMapper {

    public NurseOnboardingResponse toResponse(NurseProfile profile,
                                              NurseKyc kyc,
                                              List<NurseCertification> certifications,
                                              NurseContract latestContract) {
        List<NurseCertificationResponse> certificationResponses = certifications.stream()
                .map(this::toCertificationResponse)
                .toList();

        NurseContractResponse contractResponse = latestContract == null
                ? null
                : toContractResponse(latestContract);

        return NurseOnboardingResponse.builder()
                .profileId(profile.getId())
                .userId(profile.getUser().getId())
                .fullName(profile.getUser().getFullName())
                .phone(profile.getUser().getPhone())
                .email(profile.getUser().getEmail())
                .licenseNumber(profile.getLicenseNumber())
                .dateOfBirth(profile.getDateOfBirth())
                .specialty(profile.getSpecialty())
                .experienceYears(profile.getExperienceYears())
                .bio(profile.getBio())
                .serviceArea(profile.getServiceArea())
                .address(profile.getAddress())
                .city(profile.getCity())
                .nurseStatus(profile.getNurseStatus())
                .rejectionReason(profile.getRejectionReason())
                .lastStatusChangedAt(profile.getLastStatusChangedAt())
                .profileCompleted(isProfileCompleted(profile))
                .kycCompleted(isKycCompleted(kyc))
                .certificationsCompleted(!certifications.isEmpty())
                .contractSigned(latestContract != null && latestContract.getStatus() == NurseContractStatus.SIGNED)
                .kyc(kyc == null ? null : toKycResponse(kyc))
                .certifications(certificationResponses)
                .latestContract(contractResponse)
                .build();
    }

    public NurseKycResponse toKycResponse(NurseKyc kyc) {
        return NurseKycResponse.builder()
                .id(kyc.getId())
                .cccdNumberMasked(maskId(kyc.getCccdNumber()))
                .cccdName(kyc.getCccdName())
                .cccdDob(kyc.getCccdDob())
                .cccdAddress(kyc.getCccdAddress())
                .hasFrontImage(kyc.getCccdFrontS3Key() != null)
                .hasBackImage(kyc.getCccdBackS3Key() != null)
                .ekycStatus(kyc.getEkycStatus())
                .reviewNote(kyc.getReviewNote())
                .reviewedAt(kyc.getReviewedAt())
                .build();
    }

    public NurseCertificationResponse toCertificationResponse(NurseCertification certification) {
        return NurseCertificationResponse.builder()
                .id(certification.getId())
                .certName(certification.getCertName())
                .issuedBy(certification.getIssuedBy())
                .issuedDate(certification.getIssuedDate())
                .expiryDate(certification.getExpiryDate())
                .hasDocument(certification.getDocumentS3Key() != null)
                .verified(certification.getIsVerified())
                .verifiedAt(certification.getVerifiedAt())
                .build();
    }

    public NurseContractResponse toContractResponse(NurseContract contract) {
        return NurseContractResponse.builder()
                .id(contract.getId())
                .contractVersion(contract.getContractVersion())
                .status(contract.getStatus())
                .signedName(contract.getSignedName())
                .signedAt(contract.getSignedAt())
                .build();
    }

    public boolean isProfileCompleted(NurseProfile profile) {
        return profile.getLicenseNumber() != null && !profile.getLicenseNumber().isBlank()
                && profile.getDateOfBirth() != null
                && profile.getSpecialty() != null
                && profile.getExperienceYears() != null
                && profile.getAddress() != null && !profile.getAddress().isBlank()
                && profile.getCity() != null && !profile.getCity().isBlank();
    }

    public boolean isKycCompleted(NurseKyc kyc) {
        return kyc != null
                && kyc.getCccdNumber() != null && !kyc.getCccdNumber().isBlank()
                && kyc.getCccdName() != null && !kyc.getCccdName().isBlank()
                && kyc.getCccdFrontS3Key() != null && !kyc.getCccdFrontS3Key().isBlank()
                && kyc.getCccdBackS3Key() != null && !kyc.getCccdBackS3Key().isBlank();
    }

    private String maskId(String value) {
        if (value == null || value.length() < 6) {
            return "***";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }
}
