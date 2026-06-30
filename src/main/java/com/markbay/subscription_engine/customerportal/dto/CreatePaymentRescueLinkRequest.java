package com.markbay.subscription_engine.customerportal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRescueLinkRequest(
        @NotNull(message = "invoiceId is required")
        UUID invoiceId
) {
}