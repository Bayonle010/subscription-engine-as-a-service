package com.markbay.subscription_engine.plan.repository;

import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    @EntityGraph(attributePaths = {"features", "product", "tenant"})
    Page<Plan> findAllByTenant_Id(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"features", "product", "tenant"})
    Page<Plan> findAllByTenant_IdAndStatus(
            UUID tenantId,
            PlanStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"features", "product", "tenant"})
    Page<Plan> findAllByTenant_IdAndProduct_Id(
            UUID tenantId,
            UUID productId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"features", "product", "tenant"})
    Page<Plan> findAllByTenant_IdAndProduct_IdAndStatus(
            UUID tenantId,
            UUID productId,
            PlanStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"features", "product", "tenant"})
    Optional<Plan> findByIdAndTenant_Id(UUID planId, UUID tenantId);

    boolean existsByTenant_IdAndProduct_IdAndNameIgnoreCase(
            UUID tenantId,
            UUID productId,
            String name
    );

    boolean existsByTenant_IdAndProduct_IdAndNameIgnoreCaseAndIdNot(
            UUID tenantId,
            UUID productId,
            String name,
            UUID planId
    );
}