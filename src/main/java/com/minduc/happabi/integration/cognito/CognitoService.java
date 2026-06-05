package com.minduc.happabi.integration.cognito;

import com.minduc.happabi.config.CognitoSecretHashUtil;
import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CognitoService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final RestTemplate restTemplate;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Value("${aws.cognito.client-secret}")
    private String clientSecret;

    @Value("${aws.cognito.domain}")
    private String cognitoDomain;

    public String signUp(RegisterRequest request) {
        SignUpResponse signUpResponse = cognitoClient.signUp(SignUpRequest.builder()
                .clientId(clientId)
                .secretHash(secretHash(request.getPhone()))
                .username(request.getPhone())
                .password(request.getPassword())
                .userAttributes(
                        AttributeType.builder().name("phone_number").value(request.getPhone()).build(),
                        AttributeType.builder().name("name").value(request.getFullName()).build()
                )
                .build());
        return signUpResponse.userSub();
    }

    public void confirmSignUp(String phone, String otpCode) {
        cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                .clientId(clientId)
                .secretHash(secretHash(phone))
                .username(phone)
                .confirmationCode(otpCode)
                .build());
    }

    public void resendConfirmationCode(String phone) {
        cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                .clientId(clientId)
                .secretHash(secretHash(phone))
                .username(phone)
                .build());
    }

    public AdminInitiateAuthResponse adminInitiateAuth(String username, String password) {
        return cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                .userPoolId(userPoolId)
                .clientId(clientId)
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .authParameters(Map.of(
                        "USERNAME", username,
                        "PASSWORD", password,
                        "SECRET_HASH", secretHash(username)
                ))
                .build());
    }

    public InitiateAuthResponse initiateRefreshAuth(String refreshToken, String username) {
        return cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                .clientId(clientId)
                .authParameters(Map.of(
                        "REFRESH_TOKEN", refreshToken,
                        "SECRET_HASH", secretHash(username)
                ))
                .build());
    }

    public void forgotPassword(String phone) {
        cognitoClient.forgotPassword(ForgotPasswordRequest.builder()
                        .clientId(clientId)
                        .secretHash(secretHash(phone))
                        .username(phone)
                        .build());
    }

    public void confirmForgotPassword(String phone, String otpCode, String newPassword) {
        cognitoClient.confirmForgotPassword(ConfirmForgotPasswordRequest.builder()
                .clientId(clientId)
                .secretHash(secretHash(phone))
                .username(phone)
                .confirmationCode(otpCode)
                .password(newPassword)
                .build());
    }

    public void adminAddUserToGroup(String username, String roleName) {
        cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .groupName(roleName)
                .build());
    }

    public void adminDeleteUser(String username) {
        cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build());
    }

    public void adminSetPermanentPassword(String username, String password) {
        cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .password(password)
                .permanent(true)
                .build());
    }

    public String adminCreateUser(String username, String password, String fullName,
                                  String email, String phoneNumber) {
        List<AttributeType> attributes = new ArrayList<>();
        attributes.add(AttributeType.builder().name("name").value(fullName).build());
        attributes.add(AttributeType.builder().name("phone_number").value(phoneNumber).build());
        attributes.add(AttributeType.builder().name("phone_number_verified").value("true").build());
        if (email != null && !email.isBlank()) {
            attributes.add(AttributeType.builder().name("email").value(email).build());
            attributes.add(AttributeType.builder().name("email_verified").value("true").build());
        }

        AdminCreateUserResponse response = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .messageAction(MessageActionType.SUPPRESS)
                .temporaryPassword(password)
                .userAttributes(attributes)
                .build());

        adminSetPermanentPassword(username, password);
        return response.user().attributes().stream()
                .filter(attribute -> "sub".equals(attribute.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    public String adminGetUserSub(String username) {
        return cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                        .userPoolId(userPoolId)
                        .username(username)
                        .build())
                .userAttributes()
                .stream()
                .filter(attribute -> "sub".equals(attribute.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    public void adminUpdatePhoneNumber(String username, String phoneNumber, boolean verified) {
        cognitoClient.adminUpdateUserAttributes(AdminUpdateUserAttributesRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .userAttributes(
                        AttributeType.builder().name("phone_number").value(phoneNumber).build(),
                        AttributeType.builder().name("phone_number_verified").value(Boolean.toString(verified)).build()
                )
                .build());
    }

    public void updateEmailWithVerification(String accessToken, String email) {
        cognitoClient.updateUserAttributes(UpdateUserAttributesRequest.builder()
                .accessToken(accessToken)
                .userAttributes(AttributeType.builder().name("email").value(email).build())
                .build());
    }

    public void updatePhoneWithVerification(String accessToken, String phoneNumber) {
        cognitoClient.updateUserAttributes(UpdateUserAttributesRequest.builder()
                .accessToken(accessToken)
                .userAttributes(AttributeType.builder().name("phone_number").value(phoneNumber).build())
                .build());
    }

    public void resendAttributeVerificationCode(String accessToken, String attributeName) {
        cognitoClient.getUserAttributeVerificationCode(GetUserAttributeVerificationCodeRequest.builder()
                .accessToken(accessToken)
                .attributeName(attributeName)
                .build());
    }

    public void verifyUserAttribute(String accessToken, String attributeName, String code) {
        cognitoClient.verifyUserAttribute(VerifyUserAttributeRequest.builder()
                .accessToken(accessToken)
                .attributeName(attributeName)
                .code(code)
                .build());
    }

    public String getCurrentUserAttribute(String accessToken, String attributeName) {
        return cognitoClient.getUser(GetUserRequest.builder()
                        .accessToken(accessToken)
                        .build())
                .userAttributes()
                .stream()
                .filter(attribute -> attributeName.equals(attribute.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    public void linkSocialIdentityToMasterUser(String masterUsername, String providerName, String providerSubject) {
        cognitoClient.adminLinkProviderForUser(AdminLinkProviderForUserRequest.builder()
                .userPoolId(userPoolId)
                .destinationUser(ProviderUserIdentifierType.builder()
                        .providerName("Cognito")
                        .providerAttributeValue(masterUsername)
                        .build())
                .sourceUser(ProviderUserIdentifierType.builder()
                        .providerName(providerName)
                        .providerAttributeName("Cognito_Subject")
                        .providerAttributeValue(providerSubject)
                        .build())
                .build());
    }

    public void globalSignOut(String accessToken) {
        cognitoClient.globalSignOut(GlobalSignOutRequest.builder()
                .accessToken(accessToken)
                .build());
    }

    public void revokeToken(String refreshToken) {
        cognitoClient.revokeToken(RevokeTokenRequest.builder()
                .token(refreshToken)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());
    }

    public ResponseEntity<Map> exchangeCodeForTokens(SocialSyncRequest request) {
        String tokenUrl = cognitoDomain + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", request.getCode());
        body.add("redirect_uri", request.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        try {
            return restTemplate.postForEntity(tokenUrl, entity, Map.class);
        } catch (HttpClientErrorException e) {
            log.warn("[Cognito] Token exchange rejected: status={}", e.getStatusCode());
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("[Cognito] Token exchange unavailable: {}", e.getMessage());
            throw e;
        }
    }

    private String secretHash(String username) {
        return CognitoSecretHashUtil.calculateSecretHash(username, clientId, clientSecret);
    }
}
