package com.markbay.subscription_engine.planswitch.entity;

import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchDirection;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchMode;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchStatus;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "plan_switch_requests",
        indexes = {
                @Index(name = "idx_plan_switch_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_plan_switch_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_plan_switch_status", columnList = "status"),
                @Index(name = "idx_plan_switch_effective_at", columnList = "effective_at"),
                @Index(name = "idx_plan_switch_created_at", columnList = "created_at")
        }
)
public class PlanSwitchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_plan_id", nullable = false)
    private Plan oldPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_plan_id", nullable = false)
    private Plan newPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlanSwitchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlanSwitchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlanSwitchDirection direction;

    @Column(name = "old_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal newAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "charge_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal chargeAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "remaining_seconds", nullable = false)
    private long remainingSeconds;

    @Column(name = "total_period_seconds", nullable = false)
    private long totalPeriodSeconds;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "billing_reference", unique = true)
    private String billingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = PlanSwitchStatus.SCHEDULED;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}