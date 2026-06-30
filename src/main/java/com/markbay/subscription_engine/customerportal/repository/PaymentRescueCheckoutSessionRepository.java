package com.markbay.subscription_engine.customerportal.repository;

import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;
import com.markbay.subscription_engine.customerportal.enums.PaymentRescueCheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRescueCheckoutSessionRepository
        extends JpaRepository<PaymentRescueCheckoutSession, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "dunningCase",
            "portalSession"
    })
    Optional<PaymentRescueCheckoutSession> findByOrderReference(
            String orderReference
    );

    @Query("""
            SELECT session.id
            FROM PaymentRescueCheckoutSession session
            WHERE session.status = :status
              AND session.createdAt <= :createdBefore
            ORDER BY session.createdAt ASC
            """)
    List<UUID> findPendingSessionIdsForReconciliation(
            @Param("status") PaymentRescueCheckoutStatus status,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "dunningCase",
            "portalSession"
    })
    @Query("""
            SELECT session
            FROM PaymentRescueCheckoutSession session
            WHERE session.id = :sessionId
            """)
    Optional<PaymentRescueCheckoutSession> findByIdForReconciliation(
            @Param("sessionId") UUID sessionId
    );
}