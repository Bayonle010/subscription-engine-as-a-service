package com.markbay.subscription_engine.ledger.repository;

import com.markbay.subscription_engine.ledger.entity.LedgerAccount;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT account
        FROM LedgerAccount account
        WHERE account.tenant.id = :tenantId
          AND account.type = :type
          AND account.currency = :currency
        """)
    Optional<LedgerAccount> findByTenantIdAndTypeAndCurrencyForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("type") LedgerAccountType type,
            @Param("currency") String currency
    );
}