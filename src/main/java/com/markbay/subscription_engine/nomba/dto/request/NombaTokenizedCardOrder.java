package com.markbay.subscription_engine.nomba.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaTokenizedCardOrder(
        String amount,
        String currency,
        String orderReference,
        String customerEmail,
        String customerId,
        String callbackUrl,
        String accountId
) {
}