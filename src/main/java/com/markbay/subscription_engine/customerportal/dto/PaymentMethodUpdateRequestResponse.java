package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.subscription.entity.Subscription;

import java.time.Instant;
import java.util.UUID;

public record PaymentMethodUpdateRequestResponse(
        UUID subscriptionId,
        String subscriptionStatus,
        boolean paymentMethodUpdateRequested,
        Instant paymentMethodUpdateRequestedAt,
        Instant paymentMethodUpdateFulfilledAt,
        Instant currentPeriodEnd,
        String message
) {
    public static PaymentMethodUpdateRequestResponse from(
            Subscription subscription,
            String message
    ) {
        return new PaymentMethodUpdateRequestResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.isPaymentMethodUpdateRequested(),
                subscription.getPaymentMethodUpdateRequestedAt(),
                subscription.getPaymentMethodUpdateFulfilledAt(),
                subscription.getCurrentPeriodEnd(),
                message
        );
    }
}