package com.markbay.subscription_engine.ledger.repository;

import com.markbay.subscription_engine.ledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID> {

    boolean existsByTransactionRef(String transactionRef);
}