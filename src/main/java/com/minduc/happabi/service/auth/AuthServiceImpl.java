package com.minduc.happabi.service.auth;

import com.minduc.happabi.dto.request.auth.ForgotPasswordRequest;
import com.minduc.happabi.dto.request.auth.LoginRequest;
import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.ResendOtpRequest;
import com.minduc.happabi.dto.request.auth.ResetPasswordRequest;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.request.auth.VerifyOtpRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RegisterService registerService;
    private final LoginService loginService;
    private final SocialAuthService socialAuthService;
    private final PasswordService passwordService;

    @Override
    public void register(RegisterRequest request) {
        registerService.register(request);
    }

    @Override
    public void verifyOtp(VerifyOtpRequest request) {
        registerService.verifyOtp(request);
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        registerService.resendOtp(request);
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        return loginService.login(request, response);
    }

    @Override
    public AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response) {
        return socialAuthService.socialSync(request, response);
    }

    @Override
    public AuthResponse refresh(HttpServletRequest request) {
        return loginService.refresh(request);
    }

    @Override
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        loginService.logout(accessToken, request, response);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        passwordService.resetPassword(request);
    }
}
