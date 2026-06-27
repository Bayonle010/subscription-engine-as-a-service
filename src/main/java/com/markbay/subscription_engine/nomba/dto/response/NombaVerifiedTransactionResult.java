package com.markbay.subscription_engine.nomba.dto.response;

import java.math.BigDecimal;

public record NombaVerifiedTransactionResult(
        boolean success,
        String status,
        String orderReference,
        String transactionReference,
        BigDecimal amount,
        String currency,
        String rawResponse
) {
}