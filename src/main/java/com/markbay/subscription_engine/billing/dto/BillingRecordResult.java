package com.markbay.subscription_engine.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingRecordResult(
        UUID invoiceId,
        UUID paymentId,
        UUID ledgerTransactionId,
        String invoiceNumber,
        String orderReference,
        String providerTransactionReference,
        String ledgerTransactionRef,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal merchantNetAmount,
        String currency
) {
}