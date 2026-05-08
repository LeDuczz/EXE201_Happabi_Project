package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserProfileResponse getMe(String cognitoSub);

    String uploadAvatar(String cognitoSub, MultipartFile file);
}
