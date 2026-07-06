package com.markbay.subscription_engine.subscription.dto.response;

import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        UUID customerId,
        UUID planId,
        UUID paymentMethodId,
        UUID checkoutSessionId,
        SubscriptionStatus status,
        BigDecimal amount,
        String currency,
        String billingInterval,
        Integer billingIntervalCount,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Boolean cancelAtPeriodEnd,
        Instant cancelledAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenant().getId(),
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getPlan().getId(),
                subscription.getPaymentMethod().getId(),
                subscription.getCheckoutSession() != null
                        ? subscription.getCheckoutSession().getId()
                        : null,
                subscription.getStatus(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getBillingInterval().name(),
                subscription.getBillingIntervalCount(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt(),
                subscription.getActivatedAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}