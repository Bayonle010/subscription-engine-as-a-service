package com.markbay.subscription_engine.nomba.dto.response;

public record NombaCheckoutOrderResult(
        String orderReference,
        String checkoutLink,
        String rawResponse
) {
}