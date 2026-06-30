package com.markbay.subscription_engine.customerportal.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customerportal.enums.PaymentRescueCheckoutStatus;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.invoice.entity.Invoice;
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
        name = "payment_rescue_checkout_sessions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_rescue_checkout_order_reference",
                        columnNames = "order_reference"
                )
        },
        indexes = {
                @Index(name = "idx_payment_rescue_checkout_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_payment_rescue_checkout_customer_id", columnList = "customer_id"),
                @Index(name = "idx_payment_rescue_checkout_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_payment_rescue_checkout_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_payment_rescue_checkout_status", columnList = "status"),
                @Index(name = "idx_payment_rescue_checkout_order_reference", columnList = "order_reference"),
                @Index(name = "idx_payment_rescue_checkout_created_at", columnList = "created_at")
        }
)
public class PaymentRescueCheckoutSession {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dunning_case_id")
    private DunningCase dunningCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_session_id")
    private CustomerPortalSession portalSession;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentRescueCheckoutStatus status;

    @Column(name = "order_reference", nullable = false, unique = true)
    private String orderReference;

    @Column(name = "provider_checkout_url", columnDefinition = "TEXT")
    private String providerCheckoutUrl;

    @Column(name = "provider_order_reference")
    private String providerOrderReference;

    @Column(name = "provider_raw_response", columnDefinition = "TEXT")
    private String providerRawResponse;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = PaymentRescueCheckoutStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}