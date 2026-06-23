package com.markbay.subscription_engine.tenant.dto;


import jakarta.validation.constraints.Email;

public record UpdateTenantRequest(

        String businessName,

        @Email(message = "Support email must be valid")
        String supportEmail,

        String logoUrl,

        String primaryColor,

        String defaultCurrency,

        String billingTimezone
) {
}