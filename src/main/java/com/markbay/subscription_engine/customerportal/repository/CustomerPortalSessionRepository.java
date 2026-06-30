package com.markbay.subscription_engine.customerportal.repository;

import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPortalSessionRepository
        extends JpaRepository<CustomerPortalSession, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "invoice",
            "dunningCase"
    })
    Optional<CustomerPortalSession> findByTokenHash(String tokenHash);
}