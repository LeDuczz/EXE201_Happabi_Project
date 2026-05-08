package com.minduc.happabi.service.auth;

import com.minduc.happabi.dto.request.*;
import com.minduc.happabi.dto.request.SocialSyncRequest;
import com.minduc.happabi.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    void register(RegisterRequest request);

    void verifyOtp(VerifyOtpRequest request);

    void resendOtp(ResendOtpRequest request);

    AuthResponse login(LoginRequest request, HttpServletResponse response);

    AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response);

    AuthResponse refresh(HttpServletRequest request);

    void logout(String accessToken, HttpServletResponse response);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
