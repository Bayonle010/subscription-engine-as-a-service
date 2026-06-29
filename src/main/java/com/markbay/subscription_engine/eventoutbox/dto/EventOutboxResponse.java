package com.markbay.subscription_engine.eventoutbox.dto;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxStatus;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;

import java.time.Instant;
import java.util.UUID;

public record EventOutboxResponse(
        UUID id,
        UUID tenantId,
        EventOutboxType eventType,
        String eventName,
        String eventReference,
        String aggregateType,
        String aggregateId,
        EventOutboxStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant processedAt,
        Instant createdAt
) {
    public static EventOutboxResponse from(EventOutbox event) {
        return new EventOutboxResponse(
                event.getId(),
                event.getTenant().getId(),
                event.getEventType(),
                event.getEventType().value(),
                event.getEventReference(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getMaxAttempts(),
                event.getNextAttemptAt(),
                event.getProcessedAt(),
                event.getCreatedAt()
        );
    }
}