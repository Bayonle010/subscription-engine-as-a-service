package com.markbay.subscription_engine.subscriptioncheckout.dto;

import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionCheckoutSessionResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        UUID planId,
        String planName,
        BigDecimal amount,
        String currency,
        PaymentMethodType paymentMethodType,
        CheckoutSessionStatus status,
        String customerEmail,
        String customerFirstName,
        String customerLastName,
        String customerPhone,
        String orderReference,
        String checkoutUrl,
        String successUrl,
        String cancelUrl,
        Instant expiresAt,
        Instant completedAt,
        Instant failedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubscriptionCheckoutSessionResponse from(
            SubscriptionCheckoutSession session
    ) {
        return new SubscriptionCheckoutSessionResponse(
                session.getId(),
                session.getTenant().getId(),
                session.getTenant().getId(),
                session.getPlan().getId(),
                session.getPlan().getName(),
                session.getAmount(),
                session.getCurrency(),
                session.getPaymentMethodType(),
                session.getStatus(),
                session.getCustomerEmail(),
                session.getCustomerFirstName(),
                session.getCustomerLastName(),
                session.getCustomerPhone(),
                session.getOrderReference(),
                session.getProviderCheckoutUrl(),
                session.getSuccessUrl(),
                session.getCancelUrl(),
                session.getExpiresAt(),
                session.getCompletedAt(),
                session.getFailedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}