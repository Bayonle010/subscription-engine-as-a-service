package com.markbay.subscription_engine.nomba.dto.response;

public record NombaWebhookPaymentData(
        String orderReference,
        String transactionReference,
        String tokenKey,
        String cardType,
        String cardLast4,
        String expiryMonth,
        String expiryYear
) {
}