package com.markbay.subscription_engine.invoice.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.invoice.enums.InvoiceBillingReason;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
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
        name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_invoices_invoice_number",
                        columnNames = "invoice_number"
                ),
                @UniqueConstraint(
                        name = "uk_invoices_checkout_session_id",
                        columnNames = "checkout_session_id"
                )
        },
        indexes = {
                @Index(name = "idx_invoices_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_invoices_customer_id", columnList = "customer_id"),
                @Index(name = "idx_invoices_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_invoices_status", columnList = "status"),
                @Index(name = "idx_invoices_created_at", columnList = "created_at")
        }
)
public class Invoice {

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id", unique = true)
    private SubscriptionCheckoutSession checkoutSession;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_reason", nullable = false, length = 40)
    private InvoiceBillingReason billingReason;

    @Column(name = "amount_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }

        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}