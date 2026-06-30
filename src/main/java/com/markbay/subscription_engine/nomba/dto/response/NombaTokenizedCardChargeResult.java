package com.markbay.subscription_engine.nomba.dto.response;

public record NombaTokenizedCardChargeResult(
        boolean success,
        String status,
        String message,
        String orderReference,
        String transactionReference,
        String rawResponse
) {
}