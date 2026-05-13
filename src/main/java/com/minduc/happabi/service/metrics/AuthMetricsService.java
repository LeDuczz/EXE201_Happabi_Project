package com.minduc.happabi.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthMetricsService {

    private final Counter.Builder registerSuccess;

    private final Counter.Builder registerFailure;

    private final Counter.Builder loginSuccess;

    private final Counter.Builder loginFailure;

    private final Counter otpVerifySuccess;

    private final Counter.Builder otpVerifyFailure;

    private final Counter otpResend;

    private final Counter.Builder socialLoginSuccess;

    private final Counter forgotPasswordRequested;

    private final Counter resetPasswordSuccess;

    private final Counter.Builder rateLimitBlocked;

    private final MeterRegistry registry;

    public AuthMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Register counter — tag biến được build lazy khi gọi
        this.registerSuccess = Counter.builder("happabi.auth.register.success")
                .description("Number of successful user registrations")
                .tag("role", "unknown"); // override khi gọi

        this.registerFailure = Counter.builder("happabi.auth.register.failure")
                .description("Number of failed user registrations")
                .tag("reason", "unknown");

        this.loginSuccess = Counter.builder("happabi.auth.login.success")
                .description("Number of successful logins")
                .tag("role", "unknown")
                .tag("provider", "LOCAL");

        this.loginFailure = Counter.builder("happabi.auth.login.failure")
                .description("Number of failed login attempts")
                .tag("reason", "unknown");

        this.otpVerifySuccess = Counter.builder("happabi.auth.otp.verify.success")
                .description("Number of successful OTP verifications")
                .register(registry);

        this.otpVerifyFailure = Counter.builder("happabi.auth.otp.verify.failure")
                .description("Number of failed OTP verifications")
                .tag("reason", "unknown");

        this.otpResend = Counter.builder("happabi.auth.otp.resend")
                .description("Number of OTP resend requests")
                .register(registry);

        this.socialLoginSuccess = Counter.builder("happabi.auth.social.login.success")
                .description("Number of successful social logins")
                .tag("provider", "unknown");

        this.forgotPasswordRequested = Counter.builder("happabi.auth.forgot_password.requested")
                .description("Number of forgot-password requests")
                .register(registry);

        this.resetPasswordSuccess = Counter.builder("happabi.auth.reset_password.success")
                .description("Number of successful password resets")
                .register(registry);

        this.rateLimitBlocked = Counter.builder("happabi.auth.rate_limit.blocked")
                .description("Number of requests blocked by rate limiter")
                .tag("endpoint", "unknown")
                .tag("type", "unknown");
    }

    public void recordRegisterSuccess(String role) {
        registerSuccess.tag("role", role).register(registry).increment();
        log.debug("[Metrics] auth.register.success role={}", role);
    }

    public void recordRegisterFailure(String reason) {
        registerFailure.tag("reason", reason).register(registry).increment();
    }

    public void recordLoginSuccess(String role, String provider) {
        loginSuccess
                .tag("role", role)
                .tag("provider", provider)
                .register(registry).increment();
        log.debug("[Metrics] auth.login.success role={} provider={}", role, provider);
    }

    public void recordLoginFailure(String reason) {
        loginFailure.tag("reason", reason).register(registry).increment();
    }

    public void recordOtpVerifySuccess() {
        otpVerifySuccess.increment();
    }

    public void recordOtpVerifyFailure(String reason) {
        otpVerifyFailure.tag("reason", reason).register(registry).increment();
    }

    public void recordOtpResend() {
        otpResend.increment();
    }

    public void recordSocialLoginSuccess(String provider) {
        socialLoginSuccess.tag("provider", provider).register(registry).increment();
        log.debug("[Metrics] auth.social.login.success provider={}", provider);
    }

    public void recordForgotPasswordRequested() {
        forgotPasswordRequested.increment();
    }

    public void recordResetPasswordSuccess() {
        resetPasswordSuccess.increment();
    }

    public void recordRateLimitBlocked(String endpoint, String type) {
        rateLimitBlocked
                .tag("endpoint", endpoint)
                .tag("type", type)
                .register(registry).increment();
        log.debug("[Metrics] auth.rate_limit.blocked endpoint={} type={}", endpoint, type);
    }
}
