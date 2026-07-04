package com.markbay.subscription_engine.merchantwithdrawal.entity;

import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalProvider;
import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.payoutaccount.entity.MerchantPayoutAccount;
import com.markbay.subscription_engine.payoutaccount.enums.PayoutDestinationType;
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
        name = "merchant_withdrawals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_withdrawal_tenant_idempotency",
                        columnNames = {"tenant_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_merchant_withdrawal_merchant_tx_ref",
                        columnNames = "merchant_tx_ref"
                )
        },
        indexes = {
                @Index(name = "idx_merchant_withdrawals_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_merchant_withdrawals_status", columnList = "status"),
                @Index(name = "idx_merchant_withdrawals_created_at", columnList = "created_at"),
                @Index(name = "idx_merchant_withdrawals_next_attempt_at", columnList = "next_attempt_at")
        }
)
public class MerchantWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_account_id")
    private MerchantPayoutAccount payoutAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 30)
    private PayoutDestinationType destinationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MerchantWithdrawalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MerchantWithdrawalProvider provider;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

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

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "merchant_tx_ref", nullable = false)
    private String merchantTxRef;

    @Column(name = "provider_transfer_id")
    private String providerTransferId;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "provider_raw_response", columnDefinition = "TEXT")
    private String providerRawResponse;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "hold_ledger_transaction_ref")
    private String holdLedgerTransactionRef;

    @Column(name = "settlement_ledger_transaction_ref")
    private String settlementLedgerTransactionRef;

    @Column(name = "reversal_ledger_transaction_ref")
    private String reversalLedgerTransactionRef;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = MerchantWithdrawalStatus.HELD;
        }

        if (provider == null) {
            provider = MerchantWithdrawalProvider.NOMBA;
        }

        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}