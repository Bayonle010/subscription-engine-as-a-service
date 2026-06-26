package com.markbay.subscription_engine.financial.entity;

import com.markbay.subscription_engine.financial.enums.FinancialAccountStatus;
import com.markbay.subscription_engine.financial.enums.FinancialProvider;
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
        name = "tenant_financial_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_financial_accounts_tenant",
                        columnNames = "tenant_id"
                ),
                @UniqueConstraint(
                        name = "uk_tenant_financial_accounts_account_ref",
                        columnNames = "account_ref"
                )
        },
        indexes = {
                @Index(name = "idx_tenant_financial_accounts_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_tenant_financial_accounts_status", columnList = "status"),
                @Index(name = "idx_tenant_financial_accounts_account_ref", columnList = "account_ref")
        }
)
public class TenantFinancialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialProvider provider;

    @Column(name = "provider_account_id")
    private String providerAccountId;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_ref", nullable = false)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialAccountStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "provider_raw_response", columnDefinition = "TEXT")
    private String providerRawResponse;

    @Column(name = "setup_completed_at")
    private Instant setupCompletedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (provider == null) {
            provider = FinancialProvider.NOMBA;
        }

        if (status == null) {
            status = FinancialAccountStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}