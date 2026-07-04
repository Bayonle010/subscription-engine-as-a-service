package com.markbay.subscription_engine.nomba.dto.response;

public record NombaBankAccountLookupResult(
        boolean success,
        String accountNumber,
        String accountName,
        String rawResponse
) {
}