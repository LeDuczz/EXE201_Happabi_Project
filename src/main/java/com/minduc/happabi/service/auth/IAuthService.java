package com.minduc.happabi.service.auth;

import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.request.auth.*;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IAuthService {

    void register(RegisterRequest request);

    void verifyOtp(VerifyOtpRequest request);

    void resendOtp(ResendOtpRequest request);

    AuthResponse login(LoginRequest request, HttpServletResponse response);

    AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response);

    void createLocalPassword(CreateLocalPasswordRequest request);

    AuthResponse refresh(HttpServletRequest request);

    void logout(String accessToken, HttpServletRequest request, HttpServletResponse response);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
