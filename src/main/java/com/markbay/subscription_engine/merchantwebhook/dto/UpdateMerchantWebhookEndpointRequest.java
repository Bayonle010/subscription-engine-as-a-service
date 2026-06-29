package com.markbay.subscription_engine.merchantwebhook.dto;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateMerchantWebhookEndpointRequest(

        @Size(max = 120, message = "Webhook endpoint name cannot exceed 120 characters")
        String name,

        String url,

        Set<EventOutboxType> subscribedEvents
) {
}