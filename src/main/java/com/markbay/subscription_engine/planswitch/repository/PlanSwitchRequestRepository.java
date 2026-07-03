package com.markbay.subscription_engine.planswitch.repository;

import com.markbay.subscription_engine.planswitch.entity.PlanSwitchRequest;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchStatus;
import jakarta.persistence.LockModeType;
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

public interface PlanSwitchRequestRepository
        extends JpaRepository<PlanSwitchRequest, UUID> {

    boolean existsBySubscription_IdAndStatusIn(
            UUID subscriptionId,
            Collection<PlanSwitchStatus> statuses
    );

    @EntityGraph(attributePaths = {
            "tenant",
            "subscription",
            "subscription.customer",
            "subscription.paymentMethod",
            "oldPlan",
            "newPlan",
            "invoice",
            "payment"
    })
    Optional<PlanSwitchRequest> findFirstBySubscription_IdAndStatusInOrderByCreatedAtDesc(
            UUID subscriptionId,
            Collection<PlanSwitchStatus> statuses
    );

    @Query("""
            SELECT request.id
            FROM PlanSwitchRequest request
            WHERE request.status = :status
              AND request.effectiveAt <= :now
            ORDER BY request.effectiveAt ASC
            """)
    List<UUID> findDueScheduledPlanSwitchIds(
            @Param("status") PlanSwitchStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "subscription",
            "subscription.customer",
            "subscription.paymentMethod",
            "oldPlan",
            "newPlan",
            "invoice",
            "payment"
    })
    @Query("""
            SELECT request
            FROM PlanSwitchRequest request
            WHERE request.id = :requestId
            """)
    Optional<PlanSwitchRequest> findByIdForUpdate(
            @Param("requestId") UUID requestId
    );
}