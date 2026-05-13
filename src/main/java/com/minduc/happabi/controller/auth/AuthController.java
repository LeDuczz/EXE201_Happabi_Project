package com.minduc.happabi.controller.auth;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.dto.request.auth.*;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.service.auth.AuthService;
import com.minduc.happabi.service.metrics.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & authorisation endpoints")
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLog;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        authService.register(request);
        auditLog.logRegister("PENDING", request.getPhone(), request.getRole().name(),
                NetworkUtils.resolveClientIp(httpRequest));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Đăng ký thành công.", null));
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
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.login(request, response);
            auditLog.logLoginSuccess(
                    authResponse.getUser().getId().toString(),
                    authResponse.getUser().getPhone(),
                    authResponse.getUser().getRoles().stream().map(Enum::name).collect(Collectors.joining(",")),
                    NetworkUtils.resolveClientIp(httpRequest)
            );
            return ResponseEntity.ok(BaseResponse.ok("Đăng nhập thành công.", authResponse));
        } catch (Exception e) {
            auditLog.logLoginFailure(request.getPhone(), e.getClass().getSimpleName(), NetworkUtils.resolveClientIp(httpRequest));
            throw e;
        }
    }

    @PostMapping("/social/sync")
    public ResponseEntity<BaseResponse<AuthResponse>> socialSync(
            @Valid @RequestBody SocialSyncRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.socialSync(request, response);
        auditLog.logSocialLogin(
                authResponse.getUser().getId().toString(),
                String.join(",", authResponse.getUser().getLinkedProviders()),
                NetworkUtils.resolveClientIp(httpRequest)
        );
        return ResponseEntity.ok(BaseResponse.ok("Đăng nhập bằng tài khoản xã hội thành công.", authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            HttpServletRequest request) {
        AuthResponse authResponse = authService.refresh(request);
        return ResponseEntity.ok(BaseResponse.ok("Token đã được làm mới.", authResponse));
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
        return ResponseEntity.ok(BaseResponse.ok("Đăng xuất thành công."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.forgotPassword(request);
        auditLog.logForgotPassword(request.getPhone(), NetworkUtils.resolveClientIp(httpRequest));
        return ResponseEntity.ok(BaseResponse.ok("Mã OTP đặt lại mật khẩu đã được gửi qua SMS."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.resetPassword(request);
        auditLog.logResetPasswordSuccess(request.getPhone(), NetworkUtils.resolveClientIp(httpRequest));
        return ResponseEntity.ok(BaseResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

}
