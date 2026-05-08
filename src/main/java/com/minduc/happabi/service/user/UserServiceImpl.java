package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.response.UserProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.AuthErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.s3.S3Service;
import com.minduc.happabi.service.s3.S3ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final S3Service s3ServiceImpl;
    private final UserMapper userMapper;

    @Override
    public UserProfileResponse getMe(String cognitoSub) {
        User user = findBySub(cognitoSub);
        String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
        return userMapper.toProfileResponse(user, avatarUrl);
    }

    @Override
    @Transactional
    public String uploadAvatar(String cognitoSub, MultipartFile file) {
        User user = findBySub(cognitoSub);

        // Delete old avatar from S3 (no-op if null/blank)
        String oldKey = user.getAvatarS3Key();
        if (oldKey != null && !oldKey.isBlank()) {
            s3ServiceImpl.delete(oldKey);
            log.info("[Avatar] Deleted old avatar: userId={} key={}", user.getId(), oldKey);
        }

        // Upload new avatar
        String newKey = s3ServiceImpl.upload("avatars", user.getId().toString(), file);
        user.setAvatarS3Key(newKey);
        userRepository.save(user);
        log.info("[Avatar] Uploaded new avatar: userId={} key={}", user.getId(), newKey);

        // Return a fresh pre-signed URL (1 hour TTL)
        return s3ServiceImpl.presign(newKey);
    }

    private User findBySub(String cognitoSub) {
        return userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }
}
