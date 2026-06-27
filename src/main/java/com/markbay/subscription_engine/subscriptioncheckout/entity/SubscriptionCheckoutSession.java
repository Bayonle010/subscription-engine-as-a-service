package com.markbay.subscription_engine.subscriptioncheckout.entity;

import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
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
        name = "subscription_checkout_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_subscription_checkout_sessions_order_reference",
                        columnNames = "order_reference"
                )
        },
        indexes = {
                @Index(name = "idx_checkout_sessions_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_checkout_sessions_plan_id", columnList = "plan_id"),
                @Index(name = "idx_checkout_sessions_status", columnList = "status"),
                @Index(name = "idx_checkout_sessions_order_reference", columnList = "order_reference"),
                @Index(name = "idx_checkout_sessions_created_at", columnList = "created_at")
        }
)
public class SubscriptionCheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type", nullable = false, length = 30)
    private PaymentMethodType paymentMethodType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CheckoutSessionStatus status;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_first_name")
    private String customerFirstName;

    @Column(name = "customer_last_name")
    private String customerLastName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "order_reference", nullable = false, unique = true)
    private String orderReference;

    @Column(name = "provider_checkout_url", columnDefinition = "TEXT")
    private String providerCheckoutUrl;

    @Column(name = "provider_order_reference")
    private String providerOrderReference;

    @Column(name = "provider_raw_response", columnDefinition = "TEXT")
    private String providerRawResponse;

    @Column(name = "success_url", columnDefinition = "TEXT")
    private String successUrl;

    @Column(name = "cancel_url", columnDefinition = "TEXT")
    private String cancelUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = CheckoutSessionStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}