package com.markbay.subscription_engine.ledger.entity;

import com.markbay.subscription_engine.ledger.enums.LedgerAccountStatus;
import com.markbay.subscription_engine.ledger.enums.LedgerAccountType;
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
        name = "ledger_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ledger_accounts_tenant_type_currency",
                        columnNames = {"tenant_id", "account_type", "currency"}
                ),
                @UniqueConstraint(
                        name = "uk_ledger_accounts_code",
                        columnNames = "code"
                )
        },
        indexes = {
                @Index(name = "idx_ledger_accounts_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_ledger_accounts_type", columnList = "account_type"),
                @Index(name = "idx_ledger_accounts_status", columnList = "status")
        }
)
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private LedgerAccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerAccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        if (status == null) {
            status = LedgerAccountStatus.ACTIVE;
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