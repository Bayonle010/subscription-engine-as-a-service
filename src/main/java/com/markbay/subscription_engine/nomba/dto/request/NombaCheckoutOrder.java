package com.markbay.subscription_engine.nomba.dto.request;

import java.util.List;
import java.util.Map;

public record NombaCheckoutOrder(
        String amount,
        String currency,
        String orderReference,
        String callbackUrl,
        String customerEmail,
        String customerId,
        String accountId,
        Map<String, String> orderMetaData,
        List<String> allowedPaymentMethods
) {
}