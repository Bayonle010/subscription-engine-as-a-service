package com.markbay.subscription_engine.merchantwebhook.dto;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookEndpointStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MerchantWebhookEndpointResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        String name,
        String url,
        MerchantWebhookEndpointStatus status,
        Set<EventOutboxType> subscribedEvents,
        String signingSecret,
        Instant createdAt,
        Instant updatedAt
) {
    public static MerchantWebhookEndpointResponse from(
            MerchantWebhookEndpoint endpoint,
            boolean includeSecret
    ) {
        return new MerchantWebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getTenant().getId(),
                endpoint.getTenant().getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getStatus(),
                endpoint.getSubscribedEvents(),
                includeSecret ? endpoint.getSecretKey() : null,
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}