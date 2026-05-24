package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.nurse.*;
import com.minduc.happabi.dto.response.nurse.NurseCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface INurseOnboardingService {

    NurseOnboardingResponse getMyOnboarding();

    NurseOnboardingResponse updateMyProfile(UpdateNurseProfileRequest request);

    NurseOnboardingResponse updateMyKyc(UpdateNurseKycRequest request, MultipartFile frontImage, MultipartFile backImage);

    NurseCertificationResponse addMyCertification(CreateNurseCertificationRequest request, MultipartFile document);

    NurseOnboardingResponse submitMyProfile();

    NurseOnboardingResponse signContract(SignNurseContractRequest request, HttpServletRequest httpRequest);

}
