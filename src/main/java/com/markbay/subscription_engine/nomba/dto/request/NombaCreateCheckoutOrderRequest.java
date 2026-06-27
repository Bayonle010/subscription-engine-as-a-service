package com.markbay.subscription_engine.nomba.dto.request;

import java.util.Map;

public record NombaCreateCheckoutOrderRequest(
        NombaCheckoutOrder order,
        Boolean tokenizeCard,
        Map<String, String> meta
) {
}