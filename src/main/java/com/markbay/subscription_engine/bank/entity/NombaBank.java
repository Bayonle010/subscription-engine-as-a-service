package com.markbay.subscription_engine.bank.entity;

import com.markbay.subscription_engine.bank.enums.BankStatus;
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
        name = "nomba_banks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_nomba_banks_code",
                        columnNames = "code"
                )
        },
        indexes = {
                @Index(name = "idx_nomba_banks_name", columnList = "name"),
                @Index(name = "idx_nomba_banks_status", columnList = "status")
        }
)
public class NombaBank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "nip_code", length = 30)
    private String nipCode;

    @Column(columnDefinition = "TEXT")
    private String logo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankStatus status;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = BankStatus.ACTIVE;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}