package com.markbay.subscription_engine.dunning.repository;

import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.dunning.enums.DunningCaseStatus;
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

public interface DunningCaseRepository extends JpaRepository<DunningCase, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice"
    })
    Optional<DunningCase> findByInvoice_Id(UUID invoiceId);

    @Query("""
            SELECT dunningCase.id
            FROM DunningCase dunningCase
            WHERE dunningCase.status IN :statuses
              AND dunningCase.nextRetryAt IS NOT NULL
              AND dunningCase.nextRetryAt <= :now
            ORDER BY dunningCase.nextRetryAt ASC
            """)
    List<UUID> findDueDunningCaseIds(
            @Param("statuses") Collection<DunningCaseStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "tenant",
            "customer",
            "subscription",
            "subscription.plan",
            "subscription.paymentMethod",
            "invoice"
    })
    @Query("""
            SELECT dunningCase
            FROM DunningCase dunningCase
            WHERE dunningCase.id = :caseId
            """)
    Optional<DunningCase> findByIdForUpdate(@Param("caseId") UUID caseId);
}