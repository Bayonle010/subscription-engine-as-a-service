package com.markbay.subscription_engine.subscriptioncheckout.repository;

import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
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

public interface SubscriptionCheckoutSessionRepository
        extends JpaRepository<SubscriptionCheckoutSession, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "plan",
            "plan.product"
    })
    Optional<SubscriptionCheckoutSession> findByIdAndTenant_Id(
            UUID sessionId,
            UUID tenantId
    );

    @EntityGraph(attributePaths = {
            "tenant",
            "plan",
            "plan.product"
    })
    Optional<SubscriptionCheckoutSession> findByOrderReference(
            String orderReference
    );

    boolean existsByOrderReference(String orderReference);

    @Query("""
            SELECT session.id
            FROM SubscriptionCheckoutSession session
            WHERE session.status = :status
              AND session.createdAt <= :createdBefore
            ORDER BY session.createdAt ASC
            """)
    List<UUID> findPendingSessionIdsForReconciliation(
            @Param("status") CheckoutSessionStatus status,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "plan",
            "plan.product"
    })
    @Query("""
            SELECT session
            FROM SubscriptionCheckoutSession session
            WHERE session.id = :sessionId
            """)
    Optional<SubscriptionCheckoutSession> findByIdForReconciliation(
            @Param("sessionId") UUID sessionId
    );
}