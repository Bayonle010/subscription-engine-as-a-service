package com.markbay.subscription_engine.merchantwithdrawal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateNombaWalletMerchantWithdrawalRequest(
        @NotBlank(message = "Receiver account ID is required")
        String receiverAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be greater than zero")
        java.math.BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        String narration
) {
}