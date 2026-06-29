package com.markbay.subscription_engine.eventoutbox.repository;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxStatus;
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


public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    Optional<EventOutbox> findByEventReference(String eventReference);

    boolean existsByEventReference(String eventReference);

    @EntityGraph(attributePaths = {"tenant"})
    Optional<EventOutbox> findWithTenantById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant"})
    @Query("""
            SELECT event
            FROM EventOutbox event
            WHERE event.status IN :statuses
              AND event.nextAttemptAt <= :now
              AND event.attemptCount < event.maxAttempts
            ORDER BY event.createdAt ASC
            """)
    List<EventOutbox> findDueEventsForUpdate(
            @Param("statuses") Collection<EventOutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );
}