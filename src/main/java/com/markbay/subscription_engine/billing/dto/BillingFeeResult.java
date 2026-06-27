package com.markbay.subscription_engine.billing.dto;

import java.math.BigDecimal;

public record BillingFeeResult(
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal merchantNetAmount,
        String currency
) {
}