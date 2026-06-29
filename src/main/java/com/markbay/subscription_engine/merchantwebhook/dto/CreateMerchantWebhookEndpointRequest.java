package com.markbay.subscription_engine.merchantwebhook.dto;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateMerchantWebhookEndpointRequest(

        @NotBlank(message = "Webhook endpoint name is required")
        @Size(max = 120, message = "Webhook endpoint name cannot exceed 120 characters")
        String name,

        @NotBlank(message = "Webhook endpoint URL is required")
        String url,

        Set<EventOutboxType> subscribedEvents
) {
}