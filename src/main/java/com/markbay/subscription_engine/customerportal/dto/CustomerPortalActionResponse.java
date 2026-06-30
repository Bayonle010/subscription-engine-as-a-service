package com.markbay.subscription_engine.customerportal.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerPortalActionResponse(
        UUID portalSessionId,
        UUID subscriptionId,
        String subscriptionStatus,
        boolean cancelAtPeriodEnd,
        Instant currentPeriodEnd,
        Instant cancelledAt,
        String message
) {
}