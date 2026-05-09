package com.minduc.happabi.service.metrics;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AuditLogService {

    public void logLoginSuccess(String userId, String phone, String role, String ip) {
        withMdc(Map.of(
                "event",  "AUTH_LOGIN_SUCCESS",
                "userId", userId,
                "phone",  mask(phone),
                "role",   role,
                "ip",     ip
        ), () -> log.info("[AUDIT] Login success | role={} ip={}", role, ip));
    }

    public void logLoginFailure(String phone, String reason, String ip) {
        withMdc(Map.of(
                "event",  "AUTH_LOGIN_FAILURE",
                "phone",  mask(phone),
                "reason", reason,
                "ip",     ip
        ), () -> log.warn("[AUDIT] Login failed | reason={} ip={}", reason, ip));
    }

    public void logRegister(String userId, String phone, String role, String ip) {
        withMdc(Map.of(
                "event",  "AUTH_REGISTER",
                "userId", userId,
                "phone",  mask(phone),
                "role",   role,
                "ip",     ip
        ), () -> log.info("[AUDIT] User registered | role={} ip={}", role, ip));
    }

    public void logSocialLogin(String userId, String provider, String ip) {
        withMdc(Map.of(
                "event",    "AUTH_SOCIAL_LOGIN",
                "userId",   userId,
                "provider", provider,
                "ip",       ip
        ), () -> log.info("[AUDIT] Social login | provider={} ip={}", provider, ip));
    }

    public void logForgotPassword(String phone, String ip) {
        withMdc(Map.of(
                "event", "AUTH_FORGOT_PASSWORD",
                "phone", mask(phone),
                "ip",    ip
        ), () -> log.info("[AUDIT] Forgot password requested | ip={}", ip));
    }

    public void logResetPasswordSuccess(String phone, String ip) {
        withMdc(Map.of(
                "event", "AUTH_RESET_PASSWORD_SUCCESS",
                "phone", mask(phone),
                "ip",    ip
        ), () -> log.info("[AUDIT] Password reset success | ip={}", ip));
    }

    public void logRateLimitBlocked(String endpoint, String identifierType, String ip) {
        withMdc(Map.of(
                "event",          "AUTH_RATE_LIMITED",
                "endpoint",       endpoint,
                "identifierType", identifierType,
                "ip",             ip
        ), () -> log.warn("[AUDIT] Rate limit blocked | endpoint={} type={} ip={}",
                endpoint, identifierType, ip));
    }

    private void withMdc(Map<String, String> fields, Runnable logStatement) {
        try {
            fields.forEach(MDC::put);
            logStatement.run();
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 6) return "****";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
