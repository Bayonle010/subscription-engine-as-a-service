package com.markbay.subscription_engine.dunning.entity;

import com.markbay.subscription_engine.dunning.enums.DunningAttemptStatus;
import com.markbay.subscription_engine.paymentattempt.entity.PaymentAttempt;
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
        name = "dunning_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_dunning_attempts_case_attempt_number",
                        columnNames = {"dunning_case_id", "attempt_number"}
                )
        },
        indexes = {
                @Index(name = "idx_dunning_attempts_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_dunning_attempts_dunning_case_id", columnList = "dunning_case_id"),
                @Index(name = "idx_dunning_attempts_status", columnList = "status"),
                @Index(name = "idx_dunning_attempts_scheduled_at", columnList = "scheduled_at"),
                @Index(name = "idx_dunning_attempts_created_at", columnList = "created_at")
        }
)
public class DunningAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dunning_case_id", nullable = false)
    private DunningCase dunningCase;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id")
    private PaymentAttempt paymentAttempt;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DunningAttemptStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "processed_at")
    private Instant processedAt;

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
            status = DunningAttemptStatus.SCHEDULED;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}