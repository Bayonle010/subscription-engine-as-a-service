package com.markbay.subscription_engine.financial.dto;

import com.markbay.subscription_engine.ledger.dto.LedgerAccountResponse;

import java.util.List;

public record TenantFinancialSetupResponse(
        TenantFinancialAccountResponse financialAccount,
        List<LedgerAccountResponse> ledgerAccounts
) {
}