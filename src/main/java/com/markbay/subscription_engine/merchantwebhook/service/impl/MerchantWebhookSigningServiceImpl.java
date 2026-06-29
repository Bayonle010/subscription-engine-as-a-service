package com.markbay.subscription_engine.merchantwebhook.service.impl;

import com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookSigningService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class MerchantWebhookSigningServiceImpl implements MerchantWebhookSigningService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Override
    public String sign(
            String payload,
            String secret,
            String timestamp
    ) {
        try {
            String signedPayload = timestamp + "." + payload;

            Mac mac = Mac.getInstance(HMAC_SHA256);

            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8),
                            HMAC_SHA256
                    )
            );

            byte[] hash = mac.doFinal(
                    signedPayload.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign merchant webhook payload", exception);
        }
    }
}