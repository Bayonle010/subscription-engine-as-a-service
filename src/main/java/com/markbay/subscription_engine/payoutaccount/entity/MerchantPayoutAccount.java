package com.markbay.subscription_engine.payoutaccount.entity;

import com.markbay.subscription_engine.payoutaccount.enums.PayoutAccountStatus;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutDestinationType;
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
        name = "merchant_payout_accounts",
        indexes = {
                @Index(name = "idx_merchant_payout_accounts_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_merchant_payout_accounts_status", columnList = "status"),
                @Index(name = "idx_merchant_payout_accounts_destination_type", columnList = "destination_type")
        }
)
public class MerchantPayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 30)
    private PayoutDestinationType destinationType;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "receiver_account_id")
    private String receiverAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutAccountStatus status;

    @Column(name = "default_account", nullable = false)
    private boolean defaultAccount;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = PayoutAccountStatus.VERIFIED;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}