package com.markbay.subscription_engine.eventoutbox.entity;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxStatus;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "event_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_outbox_event_reference",
                        columnNames = "event_reference"
                )
        },
        indexes = {
                @Index(name = "idx_event_outbox_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_event_outbox_event_type", columnList = "event_type"),
                @Index(name = "idx_event_outbox_status", columnList = "status"),
                @Index(name = "idx_event_outbox_next_attempt_at", columnList = "next_attempt_at"),
                @Index(name = "idx_event_outbox_created_at", columnList = "created_at")
        }
)
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private EventOutboxType eventType;

    @Column(name = "event_reference", nullable = false, unique = true)
    private String eventReference;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = EventOutboxStatus.PENDING;
        }

        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

}