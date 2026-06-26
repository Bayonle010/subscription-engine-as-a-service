package com.markbay.subscription_engine.financial.dto;

import com.markbay.subscription_engine.financial.entity.TenantFinancialAccount;
import com.markbay.subscription_engine.financial.enums.FinancialAccountStatus;
import com.markbay.subscription_engine.financial.enums.FinancialProvider;

import java.time.Instant;
import java.util.UUID;

public record TenantFinancialAccountResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        FinancialProvider provider,
        String providerAccountId,
        String accountName,
        String accountRef,
        FinancialAccountStatus status,
        String failureReason,
        Instant setupCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantFinancialAccountResponse from(
            TenantFinancialAccount account
    ) {
        return new TenantFinancialAccountResponse(
                account.getId(),
                account.getTenant().getId(),
                account.getTenant().getId(),
                account.getProvider(),
                account.getProviderAccountId(),
                account.getAccountName(),
                account.getAccountRef(),
                account.getStatus(),
                account.getFailureReason(),
                account.getSetupCompletedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}