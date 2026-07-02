package com.markbay.subscription_engine.renewalcheckout.repository;

import com.markbay.subscription_engine.renewalcheckout.entity.RenewalCheckoutSession;
import com.markbay.subscription_engine.renewalcheckout.enums.RenewalCheckoutStatus;
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

public interface RenewalCheckoutSessionRepository
        extends JpaRepository<RenewalCheckoutSession, UUID> {

    boolean existsByInvoice_Id(UUID invoiceId);

    Optional<RenewalCheckoutSession> findByInvoice_Id(UUID invoiceId);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice",
            "oldPaymentMethod",
            "newPaymentMethod"
    })
    Optional<RenewalCheckoutSession> findByOrderReference(String orderReference);

    @Query("""
            SELECT session.id
            FROM RenewalCheckoutSession session
            WHERE session.status = :status
              AND session.createdAt <= :createdBefore
            ORDER BY session.createdAt ASC
            """)
    List<UUID> findPendingSessionIdsForReconciliation(
            @Param("status") RenewalCheckoutStatus status,
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
            "oldPaymentMethod",
            "newPaymentMethod"
    })
    @Query("""
            SELECT session
            FROM RenewalCheckoutSession session
            WHERE session.id = :sessionId
            """)
    Optional<RenewalCheckoutSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );
}