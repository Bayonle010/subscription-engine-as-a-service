package com.markbay.subscription_engine.financial.service;

import com.markbay.subscription_engine.financial.dto.SetupTenantFinancialAccountRequest;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountBalanceResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialSetupResponse;
import com.markbay.subscription_engine.financial.entity.TenantFinancialAccount;

import java.util.UUID;

public interface TenantFinancialAccountService {

    TenantFinancialSetupResponse setupFinancialAccount(
            SetupTenantFinancialAccountRequest request
    );

    TenantFinancialAccountResponse getCurrentTenantFinancialAccount();

    TenantFinancialAccountBalanceResponse getCurrentTenantSubAccountBalance();

    TenantFinancialAccount requireActiveFinancialAccount(UUID tenantId);
}