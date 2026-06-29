package com.markbay.subscription_engine.webhook.dto;

public record NombaWebhookAckResponse(
        String status,
        String message
) {
}
