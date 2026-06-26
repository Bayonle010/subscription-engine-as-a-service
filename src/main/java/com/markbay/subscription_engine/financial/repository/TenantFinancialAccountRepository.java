package com.markbay.subscription_engine.financial.repository;

import com.markbay.subscription_engine.financial.entity.TenantFinancialAccount;
import com.markbay.subscription_engine.financial.enums.FinancialAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantFinancialAccountRepository
        extends JpaRepository<TenantFinancialAccount, UUID> {

    Optional<TenantFinancialAccount> findByTenant_Id(UUID tenantId);

    Optional<TenantFinancialAccount> findByTenant_IdAndStatus(
            UUID tenantId,
            FinancialAccountStatus status
    );

    boolean existsByAccountRef(String accountRef);
}