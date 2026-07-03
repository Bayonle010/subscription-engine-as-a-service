package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CustomerPortalSubscriptionResponse(
        UUID portalSessionId,
        Instant portalSessionExpiresAt,
        UUID customerId,
        String customerEmail,
        String customerName,
        UUID subscriptionId,
        UUID planId,
        String planName,
        String status,
        BigDecimal amount,
        String currency,
        boolean cancelAtPeriodEnd,
        boolean paymentMethodUpdateRequested,
        Instant paymentMethodUpdateRequestedAt,
        Instant paymentMethodUpdateFulfilledAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant cancelledAt,
        CustomerPortalPaymentMethodResponse paymentMethod,
        List<String> availableActions
) {
    public static CustomerPortalSubscriptionResponse from(
            CustomerPortalSession session
    ) {
        Subscription subscription = session.getSubscription();

        return new CustomerPortalSubscriptionResponse(
                session.getId(),
                session.getExpiresAt(),
                session.getCustomer().getId(),
                session.getCustomer().getEmail(),
                buildCustomerName(
                        session.getCustomer().getFirstName(),
                        session.getCustomer().getLastName()
                ),
                subscription.getId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                subscription.getStatus().name(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.isCancelAtPeriodEnd(),
                subscription.isPaymentMethodUpdateRequested(),
                subscription.getPaymentMethodUpdateRequestedAt(),
                subscription.getPaymentMethodUpdateFulfilledAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCancelledAt(),
                CustomerPortalPaymentMethodResponse.from(subscription.getPaymentMethod()),
                availableActions(subscription)
        );
    }

    private static List<String> availableActions(Subscription subscription) {
        List<String> actions = new ArrayList<>();

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING) {

            if (!subscription.isCancelAtPeriodEnd()) {
                actions.add("CHANGE_PLAN");
            }

            if (subscription.isCancelAtPeriodEnd()) {
                actions.add("RESUME_CANCELLATION");
            } else {
                actions.add("CANCEL_AT_PERIOD_END");
            }

            actions.add("CANCEL_NOW");

            if (subscription.isPaymentMethodUpdateRequested()) {
                actions.add("CANCEL_PAYMENT_METHOD_UPDATE_REQUEST");
            } else {
                actions.add("REQUEST_PAYMENT_METHOD_UPDATE");
            }


        }

        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            actions.add("PAY_FAILED_INVOICE");
            actions.add("CANCEL_NOW");
        }

        return actions;
    }

    private static String buildCustomerName(
            String firstName,
            String lastName
    ) {
        String fullName = (
                safe(firstName) + " " + safe(lastName)
        ).trim();

        if (fullName.isBlank()) {
            return "there";
        }

        return fullName;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}