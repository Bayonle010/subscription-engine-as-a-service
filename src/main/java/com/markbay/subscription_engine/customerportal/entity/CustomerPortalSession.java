package com.markbay.subscription_engine.customerportal.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionPurpose;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.subscription.entity.Subscription;
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
        name = "customer_portal_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_portal_sessions_token_hash",
                        columnNames = "token_hash"
                )
        },
        indexes = {
                @Index(name = "idx_customer_portal_sessions_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_customer_portal_sessions_customer_id", columnList = "customer_id"),
                @Index(name = "idx_customer_portal_sessions_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_customer_portal_sessions_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_customer_portal_sessions_status", columnList = "status"),
                @Index(name = "idx_customer_portal_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "idx_customer_portal_sessions_created_at", columnList = "created_at")
        }
)
public class CustomerPortalSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dunning_case_id")
    private DunningCase dunningCase;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CustomerPortalSessionPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CustomerPortalSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = CustomerPortalSessionStatus.ACTIVE;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}