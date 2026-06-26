package com.markbay.subscription_engine.nomba.dto.response;

public record NombaSubAccountResult(
        String providerAccountId,
        String accountName,
        String accountRef,
        String rawResponse
) {
}