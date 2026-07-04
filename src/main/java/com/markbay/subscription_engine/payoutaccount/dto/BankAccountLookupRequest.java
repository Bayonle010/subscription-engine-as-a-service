package com.markbay.subscription_engine.payoutaccount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankAccountLookupRequest(
        @NotBlank(message = "Account number is required")
        @Size(min = 10, max = 10, message = "Account number must be 10 digits")
        String accountNumber,

        @NotBlank(message = "Bank code is required")
        String bankCode
) {
}