package com.minduc.happabi.service.user;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.user.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.response.user.MotherProfileResponse;
import com.minduc.happabi.dto.response.user.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MotherProfileRepository motherProfileRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserIdentityProviderRepository identityProviderRepository;
    private final S3Service s3ServiceImpl;
    private final UserMapper userMapper;
    private final UserCacheService userCacheService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public UserProfileResponse getMe() {
        String cognitoSub = getCurrentSubOrThrow();
        UserProfileResponse cached = userCacheService.getUserProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = findBySub(cognitoSub);
        String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
        UserProfileResponse response = userMapper.toProfileResponse(user, avatarUrl);
        userCacheService.putUserProfile(cognitoSub, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER:READ')")
    public MotherProfileResponse getMotherProfile() {
        String cognitoSub = getCurrentSubOrThrow();
        MotherProfileResponse cached = userCacheService.getMotherProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = findBySub(cognitoSub);
        String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
        MotherProfileResponse response = motherProfileRepository.findByUser(user)
                .map(m -> userMapper.toMotherProfileResponse(m, avatarUrl))
                .orElseThrow(() -> new AppException(UserErrorCode.MOTHER_PROFILE_NOT_FOUND));
        userCacheService.putMotherProfile(cognitoSub, response);
        return response;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public MotherProfileResponse updateMotherProfile(UpdateMotherProfileRequest request) {
        String cognitoSub = getCurrentSubOrThrow();
        User user = findBySub(cognitoSub);
        MotherProfile profile = motherProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(UserErrorCode.MOTHER_PROFILE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
            user.setUpdatedAt(now);
        }
        updatePhoneIfAllowed(user, request.getPhone(), now);
        updateEmailIfAllowed(user, request.getEmail(), now);

        if (request.getBabyBirthDate() != null) {
            profile.setBabyBirthDate(request.getBabyBirthDate());
        }
        if (request.getDayOfBirth() != null) {
            profile.setDayOfBirth(request.getDayOfBirth());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            profile.setCity(request.getCity());
        }
        profile.setUpdatedAt(now);

        userRepository.save(user);
        MotherProfile savedProfile = motherProfileRepository.save(profile);
        userCacheService.evictProfiles(cognitoSub);

        String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
        MotherProfileResponse response = userMapper.toMotherProfileResponse(savedProfile, avatarUrl);
        userCacheService.putMotherProfile(cognitoSub, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('NURSE:READ')")
    public NurseProfileResponse getNurseProfile() {
        String cognitoSub = getCurrentSubOrThrow();
        NurseProfileResponse cached = userCacheService.getNurseProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = findBySub(cognitoSub);
        String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
        NurseProfileResponse response = nurseProfileRepository.findByUser(user)
                .map(n -> userMapper.toNurseProfileResponse(n, avatarUrl))
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        userCacheService.putNurseProfile(cognitoSub, response);
        return response;
    }

    @Override
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public String uploadAvatar(MultipartFile file) {
        String cognitoSub = getCurrentSubOrThrow();
        User user = findBySub(cognitoSub);

        String oldKey = user.getAvatarS3Key();
        String newKey = s3ServiceImpl.upload("avatars", user.getId().toString(), file);

        user.setAvatarS3Key(newKey);
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        log.info("[Avatar] Uploaded new avatar: userId={} key={}", user.getId(), newKey);

        deleteOldAvatarIfPresent(user, oldKey);

        return s3ServiceImpl.presign(newKey);
    }

    private void updatePhoneIfAllowed(User user, String phone, OffsetDateTime now) {
        if (phone == null) {
            return;
        }
        if (user.getPhone() != null && !user.getPhone().equals(phone)) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET);
        }
        if (user.getPhone() == null) {
            user.setPhone(phone);
            user.setUpdatedAt(now);
        }
    }

    private void updateEmailIfAllowed(User user, String email, OffsetDateTime now) {
        if (email == null) {
            return;
        }
        if (user.getEmail() != null && !user.getEmail().equals(email)) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET);
        }
        if (user.getEmail() == null) {
            user.setEmail(email);
            user.setUpdatedAt(now);
        }
    }

    private void deleteOldAvatarIfPresent(User user, String oldKey) {
        if (oldKey == null || oldKey.isBlank()) {
            return;
        }

        try {
            s3ServiceImpl.delete(oldKey);
            log.info("[Avatar] Deleted old avatar: userId={} key={}", user.getId(), oldKey);
        } catch (RuntimeException e) {
            log.warn("[Avatar] Failed to delete old avatar after update: userId={} key={}", user.getId(), oldKey, e);
        }
    }

    private String getCurrentSubOrThrow() {
        return AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private User findBySub(String cognitoSub) {
        return identityProviderRepository.findUserByProviderUidWithRolesAndProviders(cognitoSub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }
}
