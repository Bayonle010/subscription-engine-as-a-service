package com.markbay.subscription_engine.nomba.dto.request;

public record NombaBankAccountLookupRequest(
        String accountNumber,
        String bankCode
) {
}