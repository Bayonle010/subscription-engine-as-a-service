package com.markbay.subscription_engine.subscription.repository;

import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod"
    })
    Optional<Subscription> findByIdAndTenant_Id(UUID subscriptionId, UUID tenantId);

    @Query("""
            SELECT subscription.id
            FROM Subscription subscription
            WHERE subscription.status = :status
              AND subscription.cancelAtPeriodEnd = false
              AND subscription.currentPeriodEnd <= :now
            ORDER BY subscription.currentPeriodEnd ASC
            """)
    List<UUID> findDueSubscriptionIds(
            @Param("status") SubscriptionStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod"
    })
    @Query("""
            SELECT subscription
            FROM Subscription subscription
            WHERE subscription.id = :subscriptionId
            """)
    Optional<Subscription> findByIdForRenewalUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );

    @Query("""
            SELECT subscription.id
            FROM Subscription subscription
            WHERE subscription.status IN :statuses
              AND subscription.cancelAtPeriodEnd = true
              AND subscription.currentPeriodEnd IS NOT NULL
              AND subscription.currentPeriodEnd <= :now
            ORDER BY subscription.currentPeriodEnd ASC
            """)
    List<UUID> findDueScheduledCancellationIds(
            @Param("statuses") Collection<SubscriptionStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod"
    })
    @Query("""
            SELECT subscription
            FROM Subscription subscription
            WHERE subscription.id = :subscriptionId
            """)
    Optional<Subscription> findByIdForScheduledCancellationUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );



    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod",
            "checkoutSession"
    })
    Page<Subscription> findAllByTenant_Id(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "plan",
            "paymentMethod",
            "checkoutSession"
    })
    Page<Subscription> findAllByTenant_IdAndStatus(
            UUID tenantId,
            SubscriptionStatus status,
            Pageable pageable
    );


    long countByTenant_Id(UUID tenantId);

    long countByTenant_IdAndStatus(UUID tenantId, SubscriptionStatus status);
}