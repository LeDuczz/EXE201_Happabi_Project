package com.minduc.happabi.controller.auth;

import com.minduc.happabi.dto.request.auth.CreateLocalPasswordRequest;
import com.minduc.happabi.dto.request.auth.ForgotPasswordRequest;
import com.minduc.happabi.dto.request.auth.LoginRequest;
import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.ResendOtpRequest;
import com.minduc.happabi.dto.request.auth.ResetPasswordRequest;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.request.auth.VerifyOtpRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.auth.RegisterResponse;
import com.minduc.happabi.service.auth.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final IAuthService authService = mock(IAuthService.class);
    private final AuthController controller = new AuthController(authService);

    @Test
    void csrfReturnsCurrentToken() {
        var response = controller.csrf(new CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-XSRF-TOKEN";
            }

            @Override
            public String getParameterName() {
                return "_csrf";
            }

            @Override
            public String getToken() {
                return "csrf-token";
            }
        });

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo("csrf-token");
    }

    @Test
    void registerReturnsCreatedResponse() {
        RegisterRequest request = new RegisterRequest();
        RegisterResponse serviceResponse = RegisterResponse.builder()
                .autoConfirmed(false)
                .otpRequired(true)
                .build();
        when(authService.register(request)).thenReturn(serviceResponse);

        var response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(serviceResponse);
    }

    @Test
    void verifyOtpDelegatesToService() {
        VerifyOtpRequest request = new VerifyOtpRequest();

        var response = controller.verifyOtp(request);

        verify(authService).verifyOtp(request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Account verified successfully.");
    }

    @Test
    void resendOtpDelegatesToService() {
        ResendOtpRequest request = new ResendOtpRequest();

        var response = controller.resendOtp(request);

        verify(authService).resendOtp(request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("OTP code");
    }

    @Test
    void loginPassesServletResponseToService() {
        LoginRequest request = new LoginRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        AuthResponse serviceResponse = AuthResponse.builder().accessToken("access").build();
        when(authService.login(request, servletResponse)).thenReturn(serviceResponse);

        var response = controller.login(request, servletResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(serviceResponse);
    }

    @Test
    void socialSyncPassesServletResponseToService() {
        SocialSyncRequest request = new SocialSyncRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        AuthResponse serviceResponse = AuthResponse.builder().accessToken("social").build();
        when(authService.socialSync(request, servletResponse)).thenReturn(serviceResponse);

        var response = controller.socialSync(request, servletResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(serviceResponse);
    }

    @Test
    void createLocalPasswordDelegatesToService() {
        CreateLocalPasswordRequest request = new CreateLocalPasswordRequest();

        var response = controller.createLocalPassword(request);

        verify(authService).createLocalPassword(request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Local password");
    }

    @Test
    void refreshReturnsNewToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        AuthResponse serviceResponse = AuthResponse.builder().accessToken("new-access").build();
        when(authService.refresh(request)).thenReturn(serviceResponse);

        var response = controller.refresh(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(serviceResponse);
    }

    @Test
    void logoutStripsBearerPrefixBeforeDelegating() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        var response = controller.logout("Bearer access-token", request, servletResponse);

        verify(authService).logout("access-token", request, servletResponse);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Logout successful.");
    }

    @Test
    void logoutAcceptsRawAccessToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        controller.logout("access-token", request, servletResponse);

        verify(authService).logout("access-token", request, servletResponse);
    }

    @Test
    void forgotPasswordDelegatesToService() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();

        var response = controller.forgotPassword(request);

        verify(authService).forgotPassword(request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Password reset OTP");
    }

    @Test
    void resetPasswordDelegatesToService() {
        ResetPasswordRequest request = new ResetPasswordRequest();

        var response = controller.resetPassword(request);

        verify(authService).resetPassword(request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Password reset successful");
    }
}
