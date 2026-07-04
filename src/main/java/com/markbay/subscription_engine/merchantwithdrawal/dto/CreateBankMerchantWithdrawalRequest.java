package com.markbay.subscription_engine.merchantwithdrawal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBankMerchantWithdrawalRequest(
        @NotNull(message = "Payout account ID is required")
        UUID payoutAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        String narration
) {
}