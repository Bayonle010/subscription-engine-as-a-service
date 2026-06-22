package com.markbay.subscription_engine.merchant.entity;

import com.markbay.subscription_engine.merchant.enums.MerchantRole;
import com.markbay.subscription_engine.merchant.enums.MerchantUserStatus;
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
        name = "merchant_users",
        indexes = {
                @Index(name = "idx_merchant_users_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_merchant_users_email", columnList = "email")
        }
)
public class MerchantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantUserStatus status;

    @Column(name = "session_version", nullable = false)
    private Long sessionVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if (role == null) {
            role = MerchantRole.OWNER;
        }

        if (status == null) {
            status = MerchantUserStatus.ACTIVE;
        }

        if (sessionVersion == null) {
            sessionVersion = 0L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}