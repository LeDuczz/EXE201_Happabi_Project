package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.request.nurse.CreateNurseCertificationRequest;
import com.minduc.happabi.dto.request.nurse.SignNurseContractRequest;
import com.minduc.happabi.dto.request.nurse.UpdateNurseKycRequest;
import com.minduc.happabi.dto.request.nurse.UpdateNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.service.nurse.INurseOnboardingService;
import com.minduc.happabi.service.nurse.NurseCertificationOnboardingService;
import com.minduc.happabi.service.nurse.NurseContractSigningService;
import com.minduc.happabi.service.nurse.NurseKycOnboardingService;
import com.minduc.happabi.service.nurse.NurseProfileOnboardingService;
import com.minduc.happabi.service.nurse.NurseSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseOnboardingServiceImpl implements INurseOnboardingService {

    private final NurseProfileOnboardingService nurseProfileOnboardingService;
    private final NurseKycOnboardingService nurseKycOnboardingService;
    private final NurseCertificationOnboardingService nurseCertificationOnboardingService;
    private final NurseSubmissionService nurseSubmissionService;
    private final NurseContractSigningService nurseContractSigningService;

    @Override
    public NurseOnboardingResponse getMyOnboarding() {
        return nurseProfileOnboardingService.getMyOnboarding();
    }

    @Override
    public NurseOnboardingResponse updateMyProfile(UpdateNurseProfileRequest request) {
        return nurseProfileOnboardingService.updateMyProfile(request);
    }

    @Override
    public NurseOnboardingResponse updateMyKyc(UpdateNurseKycRequest request,
                                               MultipartFile frontImage,
                                               MultipartFile backImage) {
        return nurseKycOnboardingService.updateMyKyc(request, frontImage, backImage);
    }

    @Override
    public NurseCertificationResponse addMyCertification(CreateNurseCertificationRequest request,
                                                         MultipartFile document) {
        return nurseCertificationOnboardingService.addMyCertification(request, document);
    }

    @Override
    public NurseOnboardingResponse submitMyProfile() {
        return nurseSubmissionService.submitMyProfile();
    }

    @Override
    public NurseOnboardingResponse signContract(SignNurseContractRequest request, HttpServletRequest httpRequest) {
        return nurseContractSigningService.signContract(request, httpRequest);
    }
}
