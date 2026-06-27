package com.markbay.subscription_engine.ledger.dto;

import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerBalanceResponse(
        UUID accountId,
        UUID tenantId,
        UUID ledgerAccountId,
        LedgerAccountType ledgerAccountType,
        BigDecimal availableBalance,
        String currency,
        Instant updatedAt
) {
    public static LedgerBalanceResponse from(LedgerAccount ledgerAccount) {
        return new LedgerBalanceResponse(
                ledgerAccount.getTenant().getId(),
                ledgerAccount.getTenant().getId(),
                ledgerAccount.getId(),
                ledgerAccount.getType(),
                ledgerAccount.getBalance(),
                ledgerAccount.getCurrency(),
                ledgerAccount.getUpdatedAt()
        );
    }
}