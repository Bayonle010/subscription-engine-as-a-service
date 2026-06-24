package com.markbay.subscription_engine.plan.entity;

import com.markbay.subscription_engine.plan.enums.BillingInterval;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import com.markbay.subscription_engine.product.entity.Product;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "plans",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_plans_product_name",
                        columnNames = {"product_id", "name"}
                )
        },
        indexes = {
                @Index(name = "idx_plans_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_plans_product_id", columnList = "product_id"),
                @Index(name = "idx_plans_status", columnList = "status"),
                @Index(name = "idx_plans_created_at", columnList = "created_at")
        }
)
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false)
    private BillingInterval billingInterval;

    @Column(name = "billing_interval_count", nullable = false)
    private Integer billingIntervalCount;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;

    @ElementCollection
    @CollectionTable(
            name = "plan_features",
            joinColumns = @JoinColumn(name = "plan_id")
    )
    @Column(name = "feature", nullable = false)
    @Builder.Default
    private List<String> features = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = PlanStatus.ACTIVE;
        }

        if (billingIntervalCount == null) {
            billingIntervalCount = 1;
        }

        if (trialDays == null) {
            trialDays = 0;
        }

        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();

        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
    }
}