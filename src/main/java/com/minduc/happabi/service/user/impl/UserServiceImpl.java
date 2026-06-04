package com.minduc.happabi.service.user.impl;

import com.minduc.happabi.dto.request.mother.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.request.user.ConfirmUserAttributeRequest;
import com.minduc.happabi.dto.request.user.RequestEmailChangeRequest;
import com.minduc.happabi.dto.request.user.RequestPhoneChangeRequest;
import com.minduc.happabi.dto.request.user.UpdateNurseProfileDisplayRequest;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.service.user.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserProfileService userProfileService;
    private final MotherProfileAccountService motherProfileAccountService;
    private final UserAttributeChangeService userAttributeChangeService;
    private final NurseProfileService nurseProfileService;
    private final UserAvatarService userAvatarService;
    private final UserAccountLookupService userAccountLookupService;

    @Override
    public UserProfileResponse getMe() {
        return userProfileService.getMe();
    }

    @Override
    public MotherProfileResponse getMotherProfile() {
        return motherProfileAccountService.getMotherProfile();
    }

    @Override
    public MotherProfileResponse updateMotherProfile(UpdateMotherProfileRequest request) {
        return motherProfileAccountService.updateMotherProfile(request);
    }

    @Override
    public void requestEmailChange(RequestEmailChangeRequest request) {
        userAttributeChangeService.requestEmailChange(request);
    }

    @Override
    public UserProfileResponse confirmEmailChange(ConfirmUserAttributeRequest request) {
        return userAttributeChangeService.confirmEmailChange(request);
    }

    @Override
    public void requestPhoneChange(RequestPhoneChangeRequest request) {
        userAttributeChangeService.requestPhoneChange(request);
    }

    @Override
    public UserProfileResponse confirmPhoneChange(ConfirmUserAttributeRequest request) {
        return userAttributeChangeService.confirmPhoneChange(request);
    }

    @Override
    public NurseProfileResponse getNurseProfile() {
        return nurseProfileService.getNurseProfile();
    }

    @Override
    public NurseProfileResponse updateNurseProfileDisplay(UpdateNurseProfileDisplayRequest request) {
        return nurseProfileService.updateDisplayProfile(request);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        return userAvatarService.uploadAvatar(file);
    }

    @Override
    public Page<com.minduc.happabi.dto.UserDTO> getAllUsers(String searchTerm, Pageable pageable) {
        return userAccountLookupService.getAllUsers(searchTerm, pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(java.util.UUID userId) {
        userAccountLookupService.toggleUserStatus(userId);
    }

}
