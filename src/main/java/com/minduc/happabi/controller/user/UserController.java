package com.minduc.happabi.controller.user;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.user.ConfirmUserAttributeRequest;
import com.minduc.happabi.dto.request.user.RequestEmailChangeRequest;
import com.minduc.happabi.dto.request.user.RequestPhoneChangeRequest;
import com.minduc.happabi.dto.request.mother.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.service.user.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile & avatar management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMe() {
        UserProfileResponse profile = userService.getMe();
        return ResponseEntity.ok(BaseResponse.ok(profile));
    }

    @GetMapping("/me/mother-profile")
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<BaseResponse<MotherProfileResponse>> getMotherProfile() {
        MotherProfileResponse profile = userService.getMotherProfile();
        return ResponseEntity.ok(BaseResponse.ok(profile));
    }

    @PatchMapping("/me/mother-profile")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public ResponseEntity<BaseResponse<MotherProfileResponse>> updateMotherProfile(
            @Valid @RequestBody UpdateMotherProfileRequest request) {
        MotherProfileResponse profile = userService.updateMotherProfile(request);
        return ResponseEntity.ok(BaseResponse.ok(profile));
    }

    @PostMapping("/me/email/change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<Void>> requestEmailChange(
            @Valid @RequestBody RequestEmailChangeRequest request) {
        userService.requestEmailChange(request);
        return ResponseEntity.ok(BaseResponse.ok("Verification code sent to email."));
    }

    @PostMapping("/me/email/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<UserProfileResponse>> confirmEmailChange(
            @Valid @RequestBody ConfirmUserAttributeRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(userService.confirmEmailChange(request)));
    }

    @PostMapping("/me/phone/change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<Void>> requestPhoneChange(
            @Valid @RequestBody RequestPhoneChangeRequest request) {
        userService.requestPhoneChange(request);
        return ResponseEntity.ok(BaseResponse.ok("Verification code sent to phone."));
    }

    @PostMapping("/me/phone/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<UserProfileResponse>> confirmPhoneChange(
            @Valid @RequestBody ConfirmUserAttributeRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(userService.confirmPhoneChange(request)));
    }

    @GetMapping("/me/nurse-profile")
    @PreAuthorize("hasAuthority('NURSE:READ')")
    public ResponseEntity<BaseResponse<NurseProfileResponse>> getNurseProfile() {
        NurseProfileResponse profile = userService.getNurseProfile();
        return ResponseEntity.ok(BaseResponse.ok(profile));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<String>> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        String presignedUrl = userService.uploadAvatar(file);
        return ResponseEntity.ok(
                BaseResponse.ok("Avatar đã được cập nhật thành công.", presignedUrl));
    }
}
