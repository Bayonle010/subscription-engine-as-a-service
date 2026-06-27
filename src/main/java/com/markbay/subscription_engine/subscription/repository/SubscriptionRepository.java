package com.markbay.subscription_engine.subscription.repository;

import com.markbay.subscription_engine.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod",
            "checkoutSession"
    })
    Optional<Subscription> findByCheckoutSession_Id(UUID checkoutSessionId);
}