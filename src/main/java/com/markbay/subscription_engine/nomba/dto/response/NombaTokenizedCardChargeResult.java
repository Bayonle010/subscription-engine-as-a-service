package com.markbay.subscription_engine.nomba.dto.response;

public record NombaTokenizedCardChargeResult(
        boolean success,
        boolean accepted,
        boolean requiresCustomerAction,
        String status,
        String message,
        String orderId,
        String orderReference,
        String transactionReference,
        String rawResponse
) {
    public NombaTokenizedCardChargeResult(
            boolean success,
            String status,
            String message,
            String transactionReference,
            String rawResponse
    ) {
        this(
                success,
                success,
                false,
                status,
                message,
                null,
                null,
                transactionReference,
                rawResponse
        );
    }
}