package com.markbay.subscription_engine.ledger.repository;

import com.markbay.subscription_engine.ledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID> {

    boolean existsByTransactionRef(String transactionRef);

    @EntityGraph(attributePaths = {
            "tenant",
            "entries",
            "entries.ledgerAccount"
    })
    Optional<LedgerTransaction> findByTransactionRef(String transactionRef);

}