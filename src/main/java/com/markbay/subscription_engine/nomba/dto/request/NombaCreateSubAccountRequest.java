package com.markbay.subscription_engine.nomba.dto.request;

public record NombaCreateSubAccountRequest(
        String accountName,
        String accountRef
) {
}