package com.markbay.subscription_engine.payment.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.subscription.entity.Subscription;
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
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payments_tenant_order_reference",
                        columnNames = {"tenant_id", "order_reference"}
                )
        },
        indexes = {
                @Index(name = "idx_payments_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
                @Index(name = "idx_payments_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_payments_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_payments_order_reference", columnList = "order_reference"),
                @Index(name = "idx_payments_provider_transaction_reference", columnList = "provider_transaction_reference"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_created_at", columnList = "created_at")
        }
)
public class Payment {

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id", unique = true)
    private SubscriptionCheckoutSession checkoutSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private CustomerPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal platformFee;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "order_reference", nullable = false)
    private String orderReference;

    @Column(name = "provider_transaction_reference")
    private String providerTransactionReference;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "provider_raw_response", columnDefinition = "TEXT")
    private String providerRawResponse;

    @Column(name = "ledger_transaction_ref")
    private String ledgerTransactionRef;

    @Column(name = "paid_at")
    private Instant paidAt;

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
            status = PaymentStatus.PENDING;
        }

        if (platformFee == null) {
            platformFee = BigDecimal.ZERO;
        }

        if (netAmount == null) {
            netAmount = BigDecimal.ZERO;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}