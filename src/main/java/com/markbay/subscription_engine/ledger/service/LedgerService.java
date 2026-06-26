package com.markbay.subscription_engine.ledger.service;

import com.markbay.subscription_engine.ledger.dto.LedgerAccountResponse;
import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.tenant.entity.Tenant;

import java.util.List;
import java.util.UUID;

public interface LedgerService {

    List<LedgerAccount> ensureTenantLedgerAccounts(
            Tenant tenant,
            String currency
    );

    List<LedgerAccountResponse> listTenantLedgerAccounts(UUID tenantId);
}