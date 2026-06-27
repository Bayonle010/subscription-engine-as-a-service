package com.markbay.subscription_engine.subscriptioncheckout.dto;

import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateSubscriptionCheckoutSessionRequest(

        @NotNull(message = "Plan ID is required")
        UUID planId,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid")
        String customerEmail,

        @Size(max = 100, message = "Customer first name cannot exceed 100 characters")
        String customerFirstName,

        @Size(max = 100, message = "Customer last name cannot exceed 100 characters")
        String customerLastName,

        @Size(max = 30, message = "Customer phone cannot exceed 30 characters")
        String customerPhone,

        @NotNull(message = "Payment method type is required")
        PaymentMethodType paymentMethodType,

        @NotBlank(message = "Success URL is required")
        String successUrl,

        String cancelUrl,

        Map<String, String> metadata
) {
}