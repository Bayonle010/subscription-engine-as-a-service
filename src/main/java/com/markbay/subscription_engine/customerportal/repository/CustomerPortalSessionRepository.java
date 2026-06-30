package com.markbay.subscription_engine.customerportal.repository;

import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPortalSessionRepository
        extends JpaRepository<CustomerPortalSession, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "dunningCase"
    })
    Optional<CustomerPortalSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "dunningCase"
    })
    @Query("""
            SELECT session
            FROM CustomerPortalSession session
            WHERE session.tokenHash = :tokenHash
            """)
    Optional<CustomerPortalSession> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}