package com.minduc.happabi.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@UtilityClass
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CognitoSecretHashUtil {

    String HMAC_SHA256 = "HmacSHA256";

    public String calculateSecretHash(String username, String clientId, String clientSecret) {
        try {
            if (clientSecret == null || clientSecret.isBlank()) return null;

            String message = username + clientId;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(clientSecret.getBytes("UTF-8"), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(message.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating secret hash", e);
        }
    }
}
