package com.markbay.subscription_engine.merchantwebhook.dto;


public record MerchantWebhookDispatchResult(
        boolean success,
        int httpStatus,
        String responseBody,
        String errorMessage
) {
}