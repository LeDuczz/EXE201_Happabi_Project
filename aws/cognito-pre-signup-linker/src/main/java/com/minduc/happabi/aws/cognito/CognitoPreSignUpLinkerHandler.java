package com.minduc.happabi.aws.cognito;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminLinkProviderForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ProviderUserIdentifierType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CognitoPreSignUpLinkerHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String TRIGGER_EXTERNAL_PROVIDER = "PreSignUp_ExternalProvider";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("Google", "Facebook");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CognitoIdentityProviderClient cognito;
    private final boolean autoCreateMasterUser;
    private final boolean requireVerifiedEmail;
    private final boolean allowUnverifiedFacebookEmail;
    private final String masterUsernamePrefix;
    private final String masterUsernameMode;

    public CognitoPreSignUpLinkerHandler() {
        this(CognitoIdentityProviderClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build());
    }

    CognitoPreSignUpLinkerHandler(CognitoIdentityProviderClient cognito) {
        this.cognito = cognito;
        this.autoCreateMasterUser = readBooleanEnv("AUTO_CREATE_MASTER_USER", true);
        this.requireVerifiedEmail = readBooleanEnv("REQUIRE_VERIFIED_EMAIL", true);
        this.allowUnverifiedFacebookEmail = readBooleanEnv("ALLOW_UNVERIFIED_FACEBOOK_EMAIL", false);
        this.masterUsernamePrefix = readEnv("MASTER_USERNAME_PREFIX", "usr_");
        this.masterUsernameMode = readEnv("MASTER_USERNAME_MODE", "EMAIL").toUpperCase(Locale.ROOT);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String triggerSource = stringValue(event.get("triggerSource")).orElse("");
        if (!TRIGGER_EXTERNAL_PROVIDER.equals(triggerSource)) {
            return event;
        }

        String userPoolId = requiredString(event, "userPoolId");
        String externalUsername = requiredString(event, "userName");
        ExternalIdentity externalIdentity = parseExternalIdentity(externalUsername);

        if (!SUPPORTED_PROVIDERS.contains(externalIdentity.providerName())) {
            throw new AccountLinkingException("Unsupported external provider: " + externalIdentity.providerName());
        }

        Map<String, Object> request = mapValue(event.get("request"), "request");
        Map<String, Object> attributes = mapValue(request.get("userAttributes"), "request.userAttributes");

        String email = normalizeEmail(stringValue(attributes.get("email")).orElse(null));
        boolean emailVerified = booleanAttribute(attributes.get("email_verified"));
        String fullName = stringValue(attributes.get("name")).orElse("");

        if (email == null) {
            throw new AccountLinkingException("Cannot link external identity without email.");
        }
        if (requireVerifiedEmail && !emailVerified && !canTrustUnverifiedEmail(externalIdentity)) {
            throw new AccountLinkingException("Cannot link external identity because email is not verified.");
        }

        try {
            UserType masterUser = findSingleMasterUserByEmail(userPoolId, email)
                    .orElseGet(() -> createMasterUser(userPoolId, email, emailVerified, fullName, context));

            ensureMasterUserUsable(userPoolId, masterUser, context);

            String masterUsername = masterUser.username();
            linkExternalIdentity(userPoolId, masterUsername, externalIdentity, context);
            markAutoConfirmAndVerifyEmail(event);

            log(context, "Linked provider=%s subject=%s to masterUsername=%s email=%s"
                    .formatted(externalIdentity.providerName(), externalIdentity.providerSubject(), masterUsername, maskEmail(email)));
            return event;
        } catch (SdkException e) {
            log(context, "Cognito account linking failed: " + e.getMessage());
            throw e;
        }
    }

    private Optional<UserType> findSingleMasterUserByEmail(String userPoolId, String email) {
        List<UserType> candidates = new ArrayList<>();
        String paginationToken = null;

        do {
            ListUsersResponse response = cognito.listUsers(ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .filter("email = \"" + escapeCognitoFilterValue(email) + "\"")
                    .paginationToken(paginationToken)
                    .limit(60)
                    .build());

            response.users().stream()
                    .filter(user -> isMasterCandidate(user, email))
                    .forEach(candidates::add);
            paginationToken = response.paginationToken();
        } while (paginationToken != null);

        if (candidates.size() > 1) {
            throw new AccountLinkingException("Multiple master users found for email: " + maskEmail(email));
        }
        return candidates.stream().findFirst();
    }

    private boolean isMasterCandidate(UserType user, String email) {
        String username = user.username();
        if (username == null) {
            return false;
        }
        if (username.startsWith("Google_") || username.startsWith("Facebook_")) {
            return false;
        }
        return email.equals(normalizeEmail(attribute(user, "email").orElse(null)));
    }

    private UserType createMasterUser(String userPoolId, String email, boolean emailVerified,
                                      String fullName, Context context) {
        if (!autoCreateMasterUser) {
            throw new AccountLinkingException("No master user exists for email: " + maskEmail(email));
        }

        String username = createMasterUsername(email);
        String temporaryPassword = randomPassword();

        AdminCreateUserResponse response = cognito.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .temporaryPassword(temporaryPassword)
                .messageAction(MessageActionType.SUPPRESS)
                .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                .userAttributes(
                        attribute("email", email),
                        attribute("email_verified", Boolean.toString(emailVerified)),
                        attribute("name", fullName)
                )
                .build());

        cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .password(randomPassword())
                .permanent(true)
                .build());

        log(context, "Created master user username=%s email=%s".formatted(username, maskEmail(email)));
        return response.user();
    }

    private void ensureMasterUserUsable(String userPoolId, UserType masterUser, Context context) {
        if (!"FORCE_CHANGE_PASSWORD".equals(masterUser.userStatusAsString())) {
            return;
        }

        cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(masterUser.username())
                .password(randomPassword())
                .permanent(true)
                .build());

        log(context, "Converted FORCE_CHANGE_PASSWORD master user to CONFIRMED username=%s"
                .formatted(masterUser.username()));
    }

    private String createMasterUsername(String email) {
        if ("UUID".equals(masterUsernameMode)) {
            return masterUsernamePrefix + UUID.randomUUID();
        }
        return email;
    }

    private void linkExternalIdentity(String userPoolId, String masterUsername,
                                      ExternalIdentity externalIdentity, Context context) {
        cognito.adminLinkProviderForUser(AdminLinkProviderForUserRequest.builder()
                .userPoolId(userPoolId)
                .destinationUser(ProviderUserIdentifierType.builder()
                        .providerName("Cognito")
                        .providerAttributeValue(masterUsername)
                        .build())
                .sourceUser(ProviderUserIdentifierType.builder()
                        .providerName(externalIdentity.providerName())
                        .providerAttributeName("Cognito_Subject")
                        .providerAttributeValue(externalIdentity.providerSubject())
                        .build())
                .build());

        log(context, "AdminLinkProviderForUser completed for masterUsername=%s provider=%s"
                .formatted(masterUsername, externalIdentity.providerName()));
    }

    @SuppressWarnings("unchecked")
    private void markAutoConfirmAndVerifyEmail(Map<String, Object> event) {
        Map<String, Object> response = (Map<String, Object>) event.computeIfAbsent("response", ignored -> new java.util.HashMap<>());
        response.put("autoConfirmUser", true);
        response.put("autoVerifyEmail", true);
    }

    private ExternalIdentity parseExternalIdentity(String externalUsername) {
        int separator = externalUsername.indexOf('_');
        if (separator <= 0 || separator == externalUsername.length() - 1) {
            throw new AccountLinkingException("Invalid external username format: " + externalUsername);
        }
        return new ExternalIdentity(
                normalizeProviderName(externalUsername.substring(0, separator)),
                externalUsername.substring(separator + 1)
        );
    }

    private boolean canTrustUnverifiedEmail(ExternalIdentity externalIdentity) {
        return allowUnverifiedFacebookEmail && "Facebook".equals(externalIdentity.providerName());
    }

    private String normalizeProviderName(String providerName) {
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "google" -> "Google";
            case "facebook" -> "Facebook";
            default -> providerName;
        };
    }

    private static AttributeType attribute(String name, String value) {
        return AttributeType.builder().name(name).value(value == null ? "" : value).build();
    }

    private static Optional<String> attribute(UserType user, String attributeName) {
        return user.attributes().stream()
                .filter(attribute -> attributeName.equals(attribute.name()))
                .map(AttributeType::value)
                .findFirst();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "Aa1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean booleanAttribute(Object value) {
        return stringValue(value)
                .map(v -> v.equalsIgnoreCase("true"))
                .orElse(false);
    }

    private static String escapeCognitoFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String readEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static boolean readBooleanEnv(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static String requiredString(Map<String, Object> map, String key) {
        return stringValue(map.get(key))
                .orElseThrow(() -> new AccountLinkingException("Missing required field: " + key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value, String fieldName) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new AccountLinkingException("Missing or invalid object field: " + fieldName);
    }

    private static Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? Optional.empty() : Optional.of(stringValue);
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("[CognitoPreSignUpLinker] " + message + System.lineSeparator());
        }
    }

    private record ExternalIdentity(String providerName, String providerSubject) {
    }

    public static class AccountLinkingException extends RuntimeException {
        public AccountLinkingException(String message) {
            super(message);
        }
    }
}
