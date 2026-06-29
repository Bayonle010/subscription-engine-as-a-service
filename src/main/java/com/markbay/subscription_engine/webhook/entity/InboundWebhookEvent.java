package com.markbay.subscription_engine.webhook.entity;

import com.markbay.subscription_engine.webhook.enums.InboundWebhookEventStatus;
import com.markbay.subscription_engine.webhook.enums.WebhookProvider;
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
        name = "inbound_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inbound_webhook_provider_reference",
                        columnNames = {"provider", "event_reference"}
                )
        },
        indexes = {
                @Index(name = "idx_inbound_webhooks_provider", columnList = "provider"),
                @Index(name = "idx_inbound_webhooks_event_type", columnList = "event_type"),
                @Index(name = "idx_inbound_webhooks_status", columnList = "status"),
                @Index(name = "idx_inbound_webhooks_created_at", columnList = "created_at")
        }
)
public class InboundWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookProvider provider;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_reference", nullable = false)
    private String eventReference;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature")
    private String signature;

    @Column(name = "timestamp_header")
    private String timestampHeader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InboundWebhookEventStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

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
            status = InboundWebhookEventStatus.RECEIVED;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}