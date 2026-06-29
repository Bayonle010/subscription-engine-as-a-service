package com.markbay.subscription_engine.merchantwebhook.entity;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookDeliveryStatus;
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
        name = "merchant_webhook_deliveries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_webhook_delivery_endpoint_event",
                        columnNames = {"endpoint_id", "outbox_event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_merchant_webhook_delivery_reference",
                        columnNames = "delivery_reference"
                )
        },
        indexes = {
                @Index(name = "idx_webhook_deliveries_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_webhook_deliveries_endpoint_id", columnList = "endpoint_id"),
                @Index(name = "idx_webhook_deliveries_outbox_event_id", columnList = "outbox_event_id"),
                @Index(name = "idx_webhook_deliveries_status", columnList = "status"),
                @Index(name = "idx_webhook_deliveries_created_at", columnList = "created_at")
        }
)
public class MerchantWebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private MerchantWebhookEndpoint endpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbox_event_id", nullable = false)
    private EventOutbox outboxEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private EventOutboxType eventType;

    @Column(name = "delivery_reference", nullable = false, unique = true)
    private String deliveryReference;

    @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MerchantWebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = MerchantWebhookDeliveryStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}