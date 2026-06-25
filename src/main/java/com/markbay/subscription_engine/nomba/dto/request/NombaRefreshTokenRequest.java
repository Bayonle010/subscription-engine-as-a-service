package com.markbay.subscription_engine.nomba.dto.request;

public record NombaRefreshTokenRequest(
        String grantType,
        String refreshToken
) {
}