package com.minduc.happabi.service.auth;

import com.minduc.happabi.dto.request.auth.ForgotPasswordRequest;
import com.minduc.happabi.dto.request.auth.ResetPasswordRequest;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final CognitoService cognitoService;

    @TimedAction("FORGOT_PASSWORD")
    @LogExecution
    @AuditAction(action = "FORGOT_PASSWORD", resourceType = "USER")
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));

        try {
            cognitoService.forgotPassword(request.getPhone());

            log.info("[Auth] ForgotPassword OTP sent: phone={}", request.getPhone());

        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (InvalidParameterException e) {
            throw new AppException(AuthErrorCode.SOCIAL_PROVIDER_MISMATCH,
                    "This account uses social sign-in and does not have a local password to reset.");
        } catch (LimitExceededException e) {
            throw new AppException(AuthErrorCode.RATE_LIMITED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] ForgotPassword Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    @TimedAction("RESET_PASSWORD")
    @AuditAction(action = "RESET_PASSWORD", resourceType = "USER")
    @LogExecution
    public void resetPassword(ResetPasswordRequest request) {
        try {
            cognitoService.confirmForgotPassword(
                    request.getPhone(), request.getOtpCode(), request.getNewPassword());

            log.info("[Auth] ResetPassword ok: phone={}", request.getPhone());

        } catch (CodeMismatchException e) {
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            throw new AppException(AuthErrorCode.OTP_EXPIRED);
        } catch (InvalidPasswordException e) {
            throw new AppException(AuthErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (LimitExceededException e) {
            throw new AppException(AuthErrorCode.RATE_LIMITED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] ConfirmForgotPassword Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }
}
