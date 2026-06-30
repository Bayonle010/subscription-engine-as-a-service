package com.markbay.subscription_engine.customerportal.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentRescueLinkResponse(
        UUID portalSessionId,
        UUID invoiceId,
        UUID subscriptionId,
        String rescueUrl,
        Instant expiresAt
) {
}