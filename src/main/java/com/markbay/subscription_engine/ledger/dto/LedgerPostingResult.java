package com.markbay.subscription_engine.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerPostingResult(
        UUID ledgerTransactionId,
        String transactionRef,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal merchantNetAmount,
        String currency
) {
}