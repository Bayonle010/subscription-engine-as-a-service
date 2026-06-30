package com.markbay.subscription_engine.customerportal.dto;

import com.markbay.subscription_engine.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerPortalInvoiceResponse(
        UUID invoiceId,
        String invoiceNumber,
        String status,
        String billingReason,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        Instant dueAt,
        Instant paidAt,
        Instant createdAt
) {
    public static CustomerPortalInvoiceResponse from(Invoice invoice) {
        return new CustomerPortalInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus() == null ? null : invoice.getStatus().name(),
                invoice.getBillingReason() == null ? null : invoice.getBillingReason().name(),
                invoice.getAmountDue(),
                invoice.getAmountPaid(),
                invoice.getCurrency(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getDueAt(),
                invoice.getPaidAt(),
                invoice.getCreatedAt()
        );
    }
}