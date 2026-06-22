package com.markbay.subscription_engine.auth.token.entity;

import com.markbay.subscription_engine.auth.token.enums.TokenType;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
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
        name = "merchant_refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "merchant_user_id"),
                @Index(name = "idx_refresh_tokens_token_id", columnList = "token_id")
        }
)
public class MerchantRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_user_id", nullable = false)
    private MerchantUser merchantUser;

    @Column(name = "token_id", nullable = false, unique = true)
    private String tokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false)
    private boolean expired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (tokenType == null) {
            tokenType = TokenType.BEARER;
        }
    }
}