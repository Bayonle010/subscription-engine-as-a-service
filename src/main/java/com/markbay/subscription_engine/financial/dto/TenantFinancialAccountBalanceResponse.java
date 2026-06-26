package com.markbay.subscription_engine.financial.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TenantFinancialAccountBalanceResponse(
        UUID accountId,
        UUID tenantId,
        String providerAccountId,
        String accountRef,
        BigDecimal availableBalance,
        String currency,
        String rawProviderResponse
) {
}