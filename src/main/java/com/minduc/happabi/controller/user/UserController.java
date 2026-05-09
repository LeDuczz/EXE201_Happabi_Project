package com.minduc.happabi.controller.user;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.UserProfileResponse;
import com.minduc.happabi.service.user.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public ResponseEntity<BaseResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal Jwt jwt) {
        UserProfileResponse profile = userService.getMe(jwt.getSubject());
        return ResponseEntity.ok(BaseResponse.ok(profile));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<String>> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        String presignedUrl = userService.uploadAvatar(jwt.getSubject(), file);
        return ResponseEntity.ok(
                BaseResponse.ok("Avatar đã được cập nhật thành công.", presignedUrl));
    }
}
