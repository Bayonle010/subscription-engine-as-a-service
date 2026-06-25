package com.markbay.subscription_engine.apiKey.util;


import com.markbay.subscription_engine.apiKey.enums.ApiKeyMode;

import java.security.SecureRandom;
import java.util.Base64;

public class ApiCredentialGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiCredentialGenerator() {
    }

    public static String generateClientId(ApiKeyMode mode) {
        return "ci_" + mode.name().toLowerCase() + "_" + randomUrlSafe(18);
    }

    public static String generateSecretKey(ApiKeyMode mode) {
        return "sk_" + mode.name().toLowerCase() + "_" + randomUrlSafe(32);
    }

    private static String randomUrlSafe(int numberOfBytes) {
        byte[] bytes = new byte[numberOfBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String previewSecret(String secretKey) {
        if (secretKey == null || secretKey.length() < 16) {
            return "****";
        }

        return secretKey.substring(0, 12)
                + "..."
                + secretKey.substring(secretKey.length() - 4);
    }
}