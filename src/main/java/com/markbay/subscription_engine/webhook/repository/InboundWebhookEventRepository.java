package com.markbay.subscription_engine.webhook.repository;


import com.markbay.subscription_engine.webhook.entity.InboundWebhookEvent;
import com.markbay.subscription_engine.webhook.enums.InboundWebhookEventStatus;
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

public interface InboundWebhookEventRepository
        extends JpaRepository<InboundWebhookEvent, UUID> {

    @Query("""
            SELECT event.id
            FROM InboundWebhookEvent event
            WHERE event.status = :status
              AND event.updatedAt <= :updatedBefore
            ORDER BY event.updatedAt ASC
            """)
    List<UUID> findFailedEventIdsForRetry(
            @Param("status") InboundWebhookEventStatus status,
            @Param("updatedBefore") Instant updatedBefore,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"tenant"})
    @Query("""
            SELECT event
            FROM InboundWebhookEvent event
            WHERE event.id = :eventId
            """)
    Optional<InboundWebhookEvent> findByIdForUpdate(
            @Param("eventId") UUID eventId
    );
}