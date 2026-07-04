package com.markbay.subscription_engine.merchantwithdrawal.repository;

import com.markbay.subscription_engine.merchantwithdrawal.entity.MerchantWithdrawal;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
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

public interface MerchantWithdrawalRepository
        extends JpaRepository<MerchantWithdrawal, UUID> {

    Optional<MerchantWithdrawal> findByTenant_IdAndIdempotencyKey(
            UUID tenantId,
            String idempotencyKey
    );

    Optional<MerchantWithdrawal> findByMerchantTxRef(String merchantTxRef);

    List<MerchantWithdrawal> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "payoutAccount"})
    Optional<MerchantWithdrawal> findByIdAndTenant_Id(
            UUID id,
            UUID tenantId
    );

    @Query("""
            SELECT withdrawal.id
            FROM MerchantWithdrawal withdrawal
            WHERE withdrawal.status IN :statuses
              AND (withdrawal.nextAttemptAt IS NULL OR withdrawal.nextAttemptAt <= :now)
            ORDER BY withdrawal.createdAt ASC
            """)
    List<UUID> findDueWithdrawalIds(
            @Param("statuses") Collection<MerchantWithdrawalStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant", "payoutAccount"})
    @Query("""
            SELECT withdrawal
            FROM MerchantWithdrawal withdrawal
            WHERE withdrawal.id = :withdrawalId
            """)
    Optional<MerchantWithdrawal> findByIdForUpdate(
            @Param("withdrawalId") UUID withdrawalId
    );
}