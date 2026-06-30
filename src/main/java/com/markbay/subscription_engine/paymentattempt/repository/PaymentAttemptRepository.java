package com.markbay.subscription_engine.paymentattempt.repository;

import com.markbay.subscription_engine.paymentattempt.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    boolean existsByAttemptReference(String attemptReference);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "invoice",
            "paymentMethod"
    })
    Optional<PaymentAttempt> findByAttemptReference(String attemptReference);
}