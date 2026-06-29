package com.markbay.subscription_engine.merchantwebhook.dto;

import java.time.Instant;
import java.util.UUID;

public record MerchantWebhookPayload(
        UUID id,
        String event,
        String eventReference,
        UUID accountId,
        UUID tenantId,
        String aggregateType,
        String aggregateId,
        Object data,
        Instant createdAt
) {
}