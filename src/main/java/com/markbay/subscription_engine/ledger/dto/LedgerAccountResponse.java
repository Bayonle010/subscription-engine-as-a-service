package com.markbay.subscription_engine.ledger.dto;

import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountStatus;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerAccountResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        String code,
        String name,
        LedgerAccountType type,
        String currency,
        BigDecimal balance,
        LedgerAccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static LedgerAccountResponse from(LedgerAccount ledgerAccount) {
        return new LedgerAccountResponse(
                ledgerAccount.getId(),
                ledgerAccount.getTenant().getId(),
                ledgerAccount.getTenant().getId(),
                ledgerAccount.getCode(),
                ledgerAccount.getName(),
                ledgerAccount.getType(),
                ledgerAccount.getCurrency(),
                ledgerAccount.getBalance(),
                ledgerAccount.getStatus(),
                ledgerAccount.getCreatedAt(),
                ledgerAccount.getUpdatedAt()
        );
    }
}