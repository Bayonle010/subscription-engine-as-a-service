package com.markbay.subscription_engine.dunning.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.dunning.enums.DunningCaseStatus;
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
        name = "dunning_cases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_dunning_cases_invoice_id",
                        columnNames = "invoice_id"
                )
        },
        indexes = {
                @Index(name = "idx_dunning_cases_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_dunning_cases_customer_id", columnList = "customer_id"),
                @Index(name = "idx_dunning_cases_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_dunning_cases_status", columnList = "status"),
                @Index(name = "idx_dunning_cases_next_retry_at", columnList = "next_retry_at"),
                @Index(name = "idx_dunning_cases_grace_ends_at", columnList = "grace_ends_at"),
                @Index(name = "idx_dunning_cases_created_at", columnList = "created_at")
        }
)
public class DunningCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, unique = true)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DunningCaseStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_attempts", nullable = false)
    private int maxRetryAttempts;

    @Column(name = "first_failed_at", nullable = false)
    private Instant firstFailedAt;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "grace_ends_at", nullable = false)
    private Instant graceEndsAt;

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Column(name = "exhausted_at")
    private Instant exhaustedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    private String lastFailureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = DunningCaseStatus.OPEN;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}