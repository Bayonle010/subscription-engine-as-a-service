package com.markbay.subscription_engine.subscriptioncheckout.repository;

import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionCheckoutSessionRepository
        extends JpaRepository<SubscriptionCheckoutSession, UUID> {

    @EntityGraph(attributePaths = {"tenant", "plan", "plan.product"})
    Optional<SubscriptionCheckoutSession> findByIdAndTenant_Id(
            UUID sessionId,
            UUID tenantId
    );

    @EntityGraph(attributePaths = {"tenant", "plan", "plan.product"})
    Optional<SubscriptionCheckoutSession> findByOrderReference(
            String orderReference
    );

    boolean existsByOrderReference(String orderReference);
}