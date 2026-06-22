package com.markbay.subscription_engine.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UUID tenantId,
        MerchantUserDto user
) {
}