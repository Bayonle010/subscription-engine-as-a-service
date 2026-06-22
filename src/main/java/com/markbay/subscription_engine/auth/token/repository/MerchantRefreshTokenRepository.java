package com.markbay.subscription_engine.auth.token.repository;

import com.markbay.subscription_engine.auth.token.entity.MerchantRefreshToken;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRefreshTokenRepository extends JpaRepository<MerchantRefreshToken, UUID> {

    Optional<MerchantRefreshToken> findByTokenId(String tokenId);

    @Query("""
            SELECT token
            FROM MerchantRefreshToken token
            WHERE token.merchantUser.id = :merchantUserId
              AND token.expired = false
              AND token.revoked = false
            """)
    List<MerchantRefreshToken> findAllValidTokensByMerchantUserId(UUID merchantUserId);

    @Modifying
    @Query("""
            DELETE FROM MerchantRefreshToken token
            WHERE token.expiresAt < :now
               OR token.expired = true
               OR token.revoked = true
            """)
    void deleteAllExpiredOrRevokedSince(Instant now);

    boolean existsByMerchantUserAndRevokedFalseAndExpiredFalse(MerchantUser merchantUser);
}