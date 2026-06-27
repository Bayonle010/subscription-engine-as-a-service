package com.markbay.subscription_engine.paymentmethod.entity;

import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentAuthorizationStatus;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
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
        name = "customer_payment_methods",
        indexes = {
                @Index(name = "idx_payment_methods_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_payment_methods_customer_id", columnList = "customer_id"),
                @Index(name = "idx_payment_methods_provider_token", columnList = "provider_token_key"),
                @Index(name = "idx_payment_methods_status", columnList = "status")
        }
)
public class CustomerPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethodType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentAuthorizationStatus status;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_token_key", columnDefinition = "TEXT")
    private String providerTokenKey;

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_last4", length = 10)
    private String cardLast4;

    @Column(name = "expiry_month", length = 10)
    private String expiryMonth;

    @Column(name = "expiry_year", length = 10)
    private String expiryYear;

    @Column(nullable = false)
    private boolean reusable;

    @Column(name = "provider_raw_data", columnDefinition = "TEXT")
    private String providerRawData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = PaymentAuthorizationStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}