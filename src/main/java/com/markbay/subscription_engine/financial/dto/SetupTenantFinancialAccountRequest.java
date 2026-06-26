package com.markbay.subscription_engine.financial.dto;

import jakarta.validation.constraints.Size;

public record SetupTenantFinancialAccountRequest(

        @Size(max = 150, message = "Account name cannot exceed 150 characters")
        String accountName,

        @Size(max = 100, message = "Account reference cannot exceed 100 characters")
        String accountRef
) {
}