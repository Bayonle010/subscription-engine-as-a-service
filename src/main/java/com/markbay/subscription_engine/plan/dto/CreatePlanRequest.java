package com.markbay.subscription_engine.plan.dto;

import com.markbay.subscription_engine.plan.enums.BillingInterval;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreatePlanRequest(

        @NotBlank(message = "Plan name is required")
        @Size(max = 120, message = "Plan name cannot exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Plan description cannot exceed 1000 characters")
        String description,

        @NotNull(message = "Plan amount is required")
        @DecimalMin(value = "0.01", message = "Plan amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Plan amount format is invalid")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a valid 3-letter currency code")
        String currency,

        @NotNull(message = "Billing interval is required")
        BillingInterval billingInterval,

        @NotNull(message = "Billing interval count is required")
        @Min(value = 1, message = "Billing interval count must be at least 1")
        Integer billingIntervalCount,

        @Min(value = 0, message = "Trial days cannot be negative")
        Integer trialDays,

        List<@NotBlank(message = "Feature cannot be blank") String> features
) {
}