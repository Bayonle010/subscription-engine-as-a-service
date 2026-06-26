package com.markbay.subscription_engine.nomba.dto.request;

public record NombaIssueTokenRequest(
        String grantType,
        String clientId,
        String clientSecret
) {
}