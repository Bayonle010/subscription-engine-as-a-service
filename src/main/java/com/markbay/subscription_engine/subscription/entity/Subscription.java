package com.markbay.subscription_engine.subscription.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.BillingInterval;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
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
        name = "subscriptions",
        indexes = {
                @Index(name = "idx_subscriptions_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_subscriptions_customer_id", columnList = "customer_id"),
                @Index(name = "idx_subscriptions_plan_id", columnList = "plan_id"),
                @Index(name = "idx_subscriptions_status", columnList = "status"),
                @Index(name = "idx_subscriptions_current_period_end", columnList = "current_period_end")
        }
)
public class Subscription {

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
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private CustomerPaymentMethod paymentMethod;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id", unique = true)
    private SubscriptionCheckoutSession checkoutSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SubscriptionStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 30)
    private BillingInterval billingInterval;

    @Column(name = "billing_interval_count", nullable = false)
    private Integer billingIntervalCount;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_start_at")
    private Instant trialStartAt;

    @Column(name = "trial_end_at")
    private Instant trialEndAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = SubscriptionStatus.INCOMPLETE;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}