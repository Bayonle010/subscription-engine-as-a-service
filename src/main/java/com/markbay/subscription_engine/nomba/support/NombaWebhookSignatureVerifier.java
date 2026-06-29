package com.markbay.subscription_engine.nomba.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class NombaWebhookSignatureVerifier {

    private static final String SUPPORTED_ALGORITHM = "HmacSHA256";
    private static final String SUPPORTED_VERSION = "1.0.0";

    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public NombaWebhookSignatureVerifier(
            @Value("${payment.nomba.webhook-secret}") String webhookSecret,
            ObjectMapper objectMapper
    ) {
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    public boolean isValid(
            String rawBody,
            String signature,
            String algorithm,
            String version,
            String timestamp
    ) {
        if (
                isBlank(rawBody)
                        || isBlank(signature)
                        || isBlank(algorithm)
                        || isBlank(version)
                        || isBlank(timestamp)
                        || isBlank(webhookSecret)
        ) {
            log.warn("Nomba signature validation failed: required input is blank");
            return false;
        }

        if (!SUPPORTED_ALGORITHM.equalsIgnoreCase(algorithm.trim())) {
            log.warn(
                    "Nomba signature validation failed: unsupported algorithm={}",
                    algorithm
            );
            return false;
        }

        if (!SUPPORTED_VERSION.equals(version.trim())) {
            log.warn(
                    "Nomba signature version={} is not expectedVersion={}. Proceeding with validation.",
                    version,
                    SUPPORTED_VERSION
            );
        }

        try {
            String computed = generateSignature(
                    rawBody,
                    webhookSecret,
                    timestamp.trim()
            );

            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception exception) {
            log.error("Nomba signature validation failed with exception", exception);
            return false;
        }
    }

    private String generateSignature(
            String rawBody,
            String secret,
            String timestamp
    ) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);

        JsonNode data = root.path("data");
        JsonNode merchant = data.path("merchant");
        JsonNode transaction = data.path("transaction");

        String eventType = textOf(root, "event_type");
        String requestId = textOf(root, "requestId");
        String userId = textOf(merchant, "userId");
        String walletId = textOf(merchant, "walletId");
        String transactionId = textOf(transaction, "transactionId");
        String transactionType = textOf(transaction, "type");
        String transactionTime = textOf(transaction, "time");
        String responseCode = textOf(transaction, "responseCode");

        String signingPayload = String.join(
                ":",
                safe(eventType),
                safe(requestId),
                safe(userId),
                safe(walletId),
                safe(transactionId),
                safe(transactionType),
                safe(transactionTime),
                safe(responseCode),
                safe(timestamp)
        );

        Mac mac = Mac.getInstance(SUPPORTED_ALGORITHM);

        mac.init(
                new SecretKeySpec(
                        secret.getBytes(StandardCharsets.UTF_8),
                        SUPPORTED_ALGORITHM
                )
        );

        byte[] hash = mac.doFinal(
                signingPayload.getBytes(StandardCharsets.UTF_8)
        );

        return Base64.getEncoder().encodeToString(hash);
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return "";
        }

        String text = value.asText("");

        if ("null".equalsIgnoreCase(text)) {
            return "";
        }

        return text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}