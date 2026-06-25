package com.markbay.subscription_engine.apiKey.entity;

import com.markbay.subscription_engine.apiKey.enums.ApiKeyMode;
import com.markbay.subscription_engine.apiKey.enums.ApiKeyStatus;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
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
        name = "api_keys",
        indexes = {
                @Index(name = "idx_api_keys_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_api_keys_client_id", columnList = "client_id")
        }
)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private MerchantUser createdBy;

    @Column(nullable = false)
    private String name;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "secret_preview", nullable = false)
    private String secretPreview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if (mode == null) {
            mode = ApiKeyMode.TEST;
        }

        if (status == null) {
            status = ApiKeyStatus.ACTIVE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}