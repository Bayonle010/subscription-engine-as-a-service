package com.markbay.subscription_engine.ledger.repository;

import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    List<LedgerAccount> findAllByTenant_IdOrderByCreatedAtAsc(UUID tenantId);

    Optional<LedgerAccount> findByTenant_IdAndTypeAndCurrency(
            UUID tenantId,
            LedgerAccountType type,
            String currency
    );

    boolean existsByTenant_IdAndTypeAndCurrency(
            UUID tenantId,
            LedgerAccountType type,
            String currency
    );

    boolean existsByCode(String code);
}