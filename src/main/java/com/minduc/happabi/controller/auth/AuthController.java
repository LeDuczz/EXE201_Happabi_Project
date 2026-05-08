package com.minduc.happabi.controller.auth;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.*;
import com.minduc.happabi.dto.response.AuthResponse;
import com.minduc.happabi.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & authorisation endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Đăng ký thành công. Vui lòng kiểm tra SMS để lấy mã OTP.", null));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<BaseResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(BaseResponse.ok("Tài khoản đã được xác thực thành công."));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<BaseResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(BaseResponse.ok("Mã OTP đã được gửi lại qua SMS."));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(BaseResponse.ok("Đăng nhập thành công.", authResponse));
    }

    @PostMapping("/social/sync")
    public ResponseEntity<BaseResponse<AuthResponse>> socialSync(
            @Valid @RequestBody SocialSyncRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.socialSync(request, response);
        return ResponseEntity.ok(BaseResponse.ok("Đăng nhập xã hội thành công.", authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            HttpServletRequest request) {
        AuthResponse authResponse = authService.refresh(request);
        return ResponseEntity.ok(BaseResponse.ok("Token đã được làm mới.", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader, HttpServletResponse response) {
        String accessToken = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        authService.logout(accessToken, response);
        return ResponseEntity.ok(BaseResponse.ok("Đăng xuất thành công."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody com.minduc.happabi.dto.request.ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(BaseResponse.ok("Mã OTP đặt lại mật khẩu đã được gửi qua SMS."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody com.minduc.happabi.dto.request.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(BaseResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));
    }
}
