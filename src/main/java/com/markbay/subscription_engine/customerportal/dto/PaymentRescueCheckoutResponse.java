package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRescueCheckoutResponse(
        UUID checkoutSessionId,
        UUID invoiceId,
        UUID subscriptionId,
        String orderReference,
        String checkoutUrl,
        BigDecimal amount,
        String currency,
        String status,
        Instant expiresAt
) {
    public static PaymentRescueCheckoutResponse from(
            PaymentRescueCheckoutSession session
    ) {
        return new PaymentRescueCheckoutResponse(
                session.getId(),
                session.getInvoice().getId(),
                session.getSubscription().getId(),
                session.getOrderReference(),
                session.getProviderCheckoutUrl(),
                session.getAmount(),
                session.getCurrency(),
                session.getStatus().name(),
                session.getExpiresAt()
        );
    }
}