package com.minduc.happabi.service.auth;

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
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RevokeTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

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

    public InitiateAuthResponse initiateRefreshAuth(String refreshToken, String cognitoSub) {
        return cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                .clientId(clientId)
                .authParameters(Map.of(
                        "REFRESH_TOKEN", refreshToken,
                        "SECRET_HASH", secretHash(cognitoSub)
                ))
                .build());
    }

    public void forgotPassword(String phone) {
        cognitoClient.forgotPassword(
                software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.builder()
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
        return restTemplate.postForEntity(tokenUrl, entity, Map.class);
    }

    private String secretHash(String username) {
        return CognitoSecretHashUtil.calculateSecretHash(username, clientId, clientSecret);
    }
}
