package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.request.mother.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.request.user.ConfirmUserAttributeRequest;
import com.minduc.happabi.dto.request.user.RequestEmailChangeRequest;
import com.minduc.happabi.dto.request.user.RequestPhoneChangeRequest;
import com.minduc.happabi.dto.request.user.UpdateNurseProfileDisplayRequest;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IUserService {

    UserProfileResponse getMe();

    MotherProfileResponse getMotherProfile();

    MotherProfileResponse updateMotherProfile(UpdateMotherProfileRequest request);

    void requestEmailChange(RequestEmailChangeRequest request);

    UserProfileResponse confirmEmailChange(ConfirmUserAttributeRequest request);

    void requestPhoneChange(RequestPhoneChangeRequest request);

    UserProfileResponse confirmPhoneChange(ConfirmUserAttributeRequest request);

    NurseProfileResponse getNurseProfile();

    NurseProfileResponse updateNurseProfileDisplay(UpdateNurseProfileDisplayRequest request);

    String uploadAvatar(MultipartFile file);

    Page<com.minduc.happabi.dto.UserDTO> getAllUsers(String searchTerm, Pageable pageable);

    void toggleUserStatus(java.util.UUID userId);
}
