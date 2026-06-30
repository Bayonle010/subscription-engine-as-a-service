package com.markbay.subscription_engine.customerportal.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerPortalManagementLinkResponse(
        UUID portalSessionId,
        UUID customerId,
        UUID subscriptionId,
        String portalUrl,
        Instant expiresAt
) {
}