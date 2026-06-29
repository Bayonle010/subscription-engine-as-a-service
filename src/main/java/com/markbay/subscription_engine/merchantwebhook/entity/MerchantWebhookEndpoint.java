package com.markbay.subscription_engine.merchantwebhook.entity;


import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookEndpointStatus;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "merchant_webhook_endpoints",
        indexes = {
                @Index(name = "idx_merchant_webhook_endpoints_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_merchant_webhook_endpoints_status", columnList = "status"),
                @Index(name = "idx_merchant_webhook_endpoints_created_at", columnList = "created_at")
        }
)
public class MerchantWebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "secret_key", nullable = false, columnDefinition = "TEXT")
    private String secretKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MerchantWebhookEndpointStatus status;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "merchant_webhook_endpoint_events",
            joinColumns = @JoinColumn(name = "endpoint_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Set<EventOutboxType> subscribedEvents = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = MerchantWebhookEndpointStatus.ACTIVE;
        }

        if (subscribedEvents == null) {
            subscribedEvents = new HashSet<>();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean accepts(EventOutboxType eventType) {
        return subscribedEvents == null
                || subscribedEvents.isEmpty()
                || subscribedEvents.contains(eventType);
    }
}