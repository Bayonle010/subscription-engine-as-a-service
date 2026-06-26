package com.markbay.subscription_engine.nomba.dto.response;

import java.math.BigDecimal;

public record NombaSubAccountBalanceResult(
        String providerAccountId,
        BigDecimal availableBalance,
        String currency,
        String rawResponse
) {
}