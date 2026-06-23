package com.markbay.subscription_engine.apiKey.dto;



import com.markbay.subscription_engine.apiKey.entitty.ApiKey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        UUID accountId,
        String name,
        String clientId,
        String secretKey,
        String secretPreview,
        String mode,
        String status,
        Instant lastUsedAt,
        Instant revokedAt,
        Instant createdAt
) {

    public static ApiKeyResponse from(ApiKey apiKey, String rawSecretKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getTenant().getId(),
                apiKey.getName(),
                apiKey.getClientId(),
                rawSecretKey,
                apiKey.getSecretPreview(),
                apiKey.getMode().name(),
                apiKey.getStatus().name(),
                apiKey.getLastUsedAt(),
                apiKey.getRevokedAt(),
                apiKey.getCreatedAt()
        );
    }

    public static ApiKeyResponse withoutSecret(ApiKey apiKey) {
        return from(apiKey, null);
    }
}