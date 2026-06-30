package com.markbay.subscription_engine.customerportal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCustomerPortalManagementLinkRequest(
        @NotNull(message = "subscriptionId is required")
        UUID subscriptionId
) {
}