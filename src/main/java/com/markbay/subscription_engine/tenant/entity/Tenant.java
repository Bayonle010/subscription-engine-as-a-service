package com.markbay.subscription_engine.tenant.entity;

import com.markbay.subscription_engine.tenant.TenantStatus;
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
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "business_email", nullable = false, unique = true)
    private String businessEmail;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "default_currency", nullable = false)
    @Builder.Default
    private String defaultCurrency = "NGN";

    @Column(name = "billing_timezone", nullable = false)
    @Builder.Default
    private String billingTimezone = "Africa/Lagos";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

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
            status = TenantStatus.ACTIVE;
        }

        if (defaultCurrency == null) {
            defaultCurrency = "NGN";
        }

        if (billingTimezone == null) {
            billingTimezone = "Africa/Lagos";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}