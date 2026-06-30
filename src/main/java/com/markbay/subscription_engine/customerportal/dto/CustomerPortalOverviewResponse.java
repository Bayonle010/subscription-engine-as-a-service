package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.subscription.entity.Subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerPortalOverviewResponse(
        UUID portalSessionId,
        String purpose,
        Instant expiresAt,
        CustomerInfo customer,
        SubscriptionInfo subscription,
        InvoiceInfo invoice,
        DunningInfo dunning
) {
    public static CustomerPortalOverviewResponse from(CustomerPortalSession session) {
        Subscription subscription = session.getSubscription();
        Invoice invoice = session.getInvoice();
        DunningCase dunningCase = session.getDunningCase();

        return new CustomerPortalOverviewResponse(
                session.getId(),
                session.getPurpose().name(),
                session.getExpiresAt(),
                new CustomerInfo(
                        session.getCustomer().getId(),
                        session.getCustomer().getEmail(),
                        buildCustomerName(
                                session.getCustomer().getFirstName(),
                                session.getCustomer().getLastName()
                        )
                ),
                subscription == null ? null : new SubscriptionInfo(
                        subscription.getId(),
                        subscription.getPlan().getId(),
                        subscription.getPlan().getName(),
                        subscription.getStatus().name(),
                        subscription.getCurrentPeriodStart(),
                        subscription.getCurrentPeriodEnd()
                ),
                invoice == null ? null : new InvoiceInfo(
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        invoice.getStatus().name(),
                        invoice.getAmountDue(),
                        invoice.getAmountPaid(),
                        invoice.getCurrency(),
                        invoice.getDueAt()
                ),
                dunningCase == null ? null : new DunningInfo(
                        dunningCase.getId(),
                        dunningCase.getStatus().name(),
                        dunningCase.getRetryCount(),
                        dunningCase.getMaxRetryAttempts(),
                        dunningCase.getNextRetryAt(),
                        dunningCase.getGraceEndsAt(),
                        dunningCase.getLastFailureReason()
                )
        );
    }

    public record CustomerInfo(
            UUID customerId,
            String email,
            String name
    ) {
    }

    public record SubscriptionInfo(
            UUID subscriptionId,
            UUID planId,
            String planName,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {
    }

    public record InvoiceInfo(
            UUID invoiceId,
            String invoiceNumber,
            String status,
            BigDecimal amountDue,
            BigDecimal amountPaid,
            String currency,
            Instant dueAt
    ) {
    }

    public record DunningInfo(
            UUID dunningCaseId,
            String status,
            int retryCount,
            int maxRetryAttempts,
            Instant nextRetryAt,
            Instant graceEndsAt,
            String lastFailureReason
    ) {
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