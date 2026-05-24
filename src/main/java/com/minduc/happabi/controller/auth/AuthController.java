package com.minduc.happabi.controller.auth;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.auth.CreateLocalPasswordRequest;
import com.minduc.happabi.dto.request.auth.ForgotPasswordRequest;
import com.minduc.happabi.dto.request.auth.LoginRequest;
import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.ResendOtpRequest;
import com.minduc.happabi.dto.request.auth.ResetPasswordRequest;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.request.auth.VerifyOtpRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & authorisation endpoints")
public class AuthController {

    private final AuthService authService;

    @AuditAction(action = "AUTH_REGISTER", resourceType = "USER")
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Registration successful.", null));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<BaseResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(BaseResponse.ok("Account verified successfully."));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<BaseResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(BaseResponse.ok("OTP code has been resent via SMS."));
    }

    @AuditAction(action = "AUTH_LOGIN", resourceType = "USER")
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(BaseResponse.ok("Login successful.", authResponse));
    }

    @AuditAction(action = "AUTH_SOCIAL_LOGIN", resourceType = "SOCIAL_IDENTITY")
    @PostMapping("/social/sync")
    public ResponseEntity<BaseResponse<AuthResponse>> socialSync(
            @Valid @RequestBody SocialSyncRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.socialSync(request, response);
        return ResponseEntity.ok(BaseResponse.ok("Social login successful.", authResponse));
    }

    @PostMapping("/local-password")
    public ResponseEntity<BaseResponse<Void>> createLocalPassword(
            @Valid @RequestBody CreateLocalPasswordRequest request) {
        authService.createLocalPassword(request);
        return ResponseEntity.ok(BaseResponse.ok("Local password has been created."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(HttpServletRequest request) {
        AuthResponse authResponse = authService.refresh(request);
        return ResponseEntity.ok(BaseResponse.ok("Token refreshed.", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        authService.logout(accessToken, request, response);
        return ResponseEntity.ok(BaseResponse.ok("Logout successful."));
    }

    @AuditAction(action = "AUTH_FORGOT_PASSWORD", resourceType = "USER")
    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(BaseResponse.ok("Password reset OTP has been sent via SMS."));
    }

    @AuditAction(action = "AUTH_RESET_PASSWORD", resourceType = "USER")
    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(BaseResponse.ok("Password reset successful. Please sign in again."));
    }
}
