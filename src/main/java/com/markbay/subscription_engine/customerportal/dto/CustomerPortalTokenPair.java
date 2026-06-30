package com.markbay.subscription_engine.customerportal.dto;

public record CustomerPortalTokenPair(
        String rawToken,
        String tokenHash
) {
}