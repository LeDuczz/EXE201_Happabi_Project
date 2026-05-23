package com.minduc.happabi.service.user.impl;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.user.ConfirmUserAttributeRequest;
import com.minduc.happabi.dto.request.user.RequestEmailChangeRequest;
import com.minduc.happabi.dto.request.user.RequestPhoneChangeRequest;
import com.minduc.happabi.dto.request.mother.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.auth.CognitoService;
import com.minduc.happabi.service.s3.S3Service;
import com.minduc.happabi.service.user.UserCacheService;
import com.minduc.happabi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CognitoService cognitoService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("GET_ME")
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
    @LogExecution
    @TimedAction("GET_MOTHER_PROFILE")
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
    @LogExecution
    @TimedAction("UPDATE_MOTHER_PROFILE")
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
        rejectVerifiedAttributeUpdate(request.getPhone(), request.getEmail());

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
        MotherProfileResponse response = userMapper.toMotherProfileResponse(savedProfile,
                avatarUrl);
        userCacheService.putMotherProfile(cognitoSub, response);
        return response;
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("REQUEST_EMAIL_CHANGE")
    @AuditAction(action = "REQUEST_EMAIL_CHANGE", resourceType = "USER_PROFILE")
    public void requestEmailChange(RequestEmailChangeRequest request) {
        String cognitoSub = getCurrentSubOrThrow();
        User user = findBySub(cognitoSub);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                    "Email is already verified and cannot be changed.");
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET);
        }
        ensureEmailAvailableForUser(normalizedEmail, user);
        cognitoService.updateEmailWithVerification(requireAccessToken(), normalizedEmail);
        cognitoService.resendAttributeVerificationCode(requireAccessToken(), "email");
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("CONFIRM_EMAIL_CHANGE")
    public UserProfileResponse confirmEmailChange(ConfirmUserAttributeRequest request) {
        String cognitoSub = getCurrentSubOrThrow();
        cognitoService.verifyUserAttribute(requireAccessToken(), "email",
                request.getCode());

        User user = findBySub(cognitoSub);
        String verifiedEmail = cognitoService.getCurrentUserAttribute(requireAccessToken(),
                "email");
        ensureEmailAvailableForUser(verifiedEmail, user);
        user.setEmail(verifiedEmail);
        user.setEmailVerified(true);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        return userMapper.toProfileResponse(user, s3ServiceImpl.presign(user.getAvatarS3Key()));
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("REQUEST_PHONE_CHANGE")
    @AuditAction(action = "REQUEST_PHONE_CHANGE", resourceType = "USER_PROFILE")
    public void requestPhoneChange(RequestPhoneChangeRequest request) {
        String cognitoSub = getCurrentSubOrThrow();
        User user = findBySub(cognitoSub);
        if (Boolean.TRUE.equals(user.getPhoneVerified())) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                    "Phone number is already verified and cannot be changed.");
        }
        if (user.getPhone() != null && !user.getPhone().equals(request.getPhone())) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET);
        }
        ensurePhoneAvailableForUser(request.getPhone(), user);
        cognitoService.updatePhoneWithVerification(requireAccessToken(), request.getPhone());
        cognitoService.resendAttributeVerificationCode(requireAccessToken(),
                "phone_number");
    }

    @Override
    @Transactional
    @LogExecution
    @TimedAction("CONFIRM_PHONE_CHANGE")
    public UserProfileResponse confirmPhoneChange(ConfirmUserAttributeRequest request) {
        String cognitoSub = getCurrentSubOrThrow();
        cognitoService.verifyUserAttribute(requireAccessToken(), "phone_number",
                request.getCode());

        User user = findBySub(cognitoSub);
        String verifiedPhone = cognitoService.getCurrentUserAttribute(requireAccessToken(),
                "phone_number");
        ensurePhoneAvailableForUser(verifiedPhone, user);
        user.setPhone(verifiedPhone);
        user.setPhoneVerified(true);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        return userMapper.toProfileResponse(user, s3ServiceImpl.presign(user.getAvatarS3Key()));
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("GET_NURSE_PROFILE")
    public NurseProfileResponse getNurseProfile() {
        String cognitoSub = getCurrentSubOrThrow();
        NurseProfileResponse cached = userCacheService.getNurseProfile(cognitoSub)
                .orElse(null);
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
    @LogExecution
    @TimedAction("UPLOAD_AVATAR")
    @AuditAction(action = "UPLOAD_AVATAR", resourceType = "USER_PROFILE")
    public String uploadAvatar(MultipartFile file) {
        String cognitoSub = getCurrentSubOrThrow();
        User user = findBySub(cognitoSub);

        String oldKey = user.getAvatarS3Key();
        String newKey = s3ServiceImpl.upload("avatars", user.getId().toString(), file);

        user.setAvatarS3Key(newKey);
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        log.info("[Avatar] Uploaded new avatar: userId={} key={}", user.getId(), newKey);

        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(oldKey,
                    "AVATAR_REPLACED"));
        }

        return s3ServiceImpl.presign(newKey);
    }

    private void rejectVerifiedAttributeUpdate(String phone, String email) {
        if (phone != null) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                    "Use /api/v1/users/me/phone/change and /confirm to update a verified phone.");
        }
        if (email != null) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                    "Use /api/v1/users/me/email/change and /confirm to update a verified email.");
        }
    }

    private String getCurrentSubOrThrow() {
        return AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    private void ensureEmailAvailableForUser(String email, User currentUser) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(existing -> !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                            "Email is already verified by another account.");
                });
    }

    private void ensurePhoneAvailableForUser(String phone, User currentUser) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        userRepository.findByPhone(phone)
                .filter(existing -> !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                            "Phone is already verified by another account.");
                });
    }

    private String requireAccessToken() {
        return AuthUtils.getJwt()
                .map(jwt -> jwt.getTokenValue())
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED));
    }

    private User findBySub(String cognitoSub) {
        return userRepository.findByCognitoSubWithRolesAndProviders(cognitoSub)
                .or(() -> identityProviderRepository
                        .findUserByProviderUidWithRolesAndProviders(cognitoSub))
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }
}
