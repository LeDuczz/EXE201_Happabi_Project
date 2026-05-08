package com.minduc.happabi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Auth & Identity domain error codes.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ServiceErrorCode {

    // ── Registration ───────────────────────────────────────────────────────────
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,          "Email is already registered."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT,          "Phone number is already registered."),

    INVALID_ROLE_FOR_REGISTRATION(HttpStatus.BAD_REQUEST,
            "Only MOTHER and NURSE roles are allowed for phone+password registration."),

    // ── OTP ────────────────────────────────────────────────────────────────────
    OTP_INVALID(HttpStatus.BAD_REQUEST,                "Invalid OTP code. Please try again."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST,                "OTP code has expired. Please request a new one."),
    USER_ALREADY_CONFIRMED(HttpStatus.CONFLICT,        "This account has already been verified."),

    // ── Login ──────────────────────────────────────────────────────────────────
    AUTH_FAILED(HttpStatus.UNAUTHORIZED,               "Authentication failed."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,       "Incorrect phone number or password."),
    USER_NOT_CONFIRMED(HttpStatus.FORBIDDEN,           "Account is not confirmed yet. Please verify your OTP."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN,             "Your account has been disabled. Contact support."),

    // ── Social Login ───────────────────────────────────────────────────────────
    /** Google / Facebook login: chỉ MOTHER được đăng ký qua social. */
    SOCIAL_ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN,
            "Social login is only available for MOTHER accounts."),
    /** User đăng ký LOCAL nhưng cố đăng nhập bằng social (hoặc ngược lại). */
    SOCIAL_PROVIDER_MISMATCH(HttpStatus.CONFLICT,
            "This phone number is already registered with a different sign-in method."),

    // ── Token ──────────────────────────────────────────────────────────────────
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,             "Access token has expired. Please refresh."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,     "Refresh token is invalid or expired. Please log in again."),

    // ── User Lookup ────────────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,               "User not found."),

    // ── Password ───────────────────────────────────────────────────────────────
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,          "Current password is incorrect."),
    PASSWORD_POLICY_VIOLATED(HttpStatus.BAD_REQUEST,   "Password does not meet the security requirements."),

    // ── Phone validation ────────────────────────────────────────────────────────
    BAD_PHONE(HttpStatus.BAD_REQUEST,                  "Invalid or missing phone number."),

    // ── Rate Limiting ───────────────────────────────────────────────────────────
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,         "Too many requests. Please slow down and try again later.");



    private final HttpStatus httpStatus;
    private final String message;
}
