package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.customerportal.dto.CustomerPortalTokenPair;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalTokenService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CustomerPortalTokenServiceImpl implements CustomerPortalTokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public CustomerPortalTokenPair generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        return new CustomerPortalTokenPair(
                rawToken,
                hashToken(rawToken)
        );
    }

    @Override
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashedBytes);

        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash customer portal token", exception);
        }
    }
}