package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.request.user.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.response.user.MotherProfileResponse;
import com.minduc.happabi.dto.response.user.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserProfileResponse getMe();

    MotherProfileResponse getMotherProfile();

    MotherProfileResponse updateMotherProfile(UpdateMotherProfileRequest request);

    NurseProfileResponse getNurseProfile();

    String uploadAvatar(MultipartFile file);
}
