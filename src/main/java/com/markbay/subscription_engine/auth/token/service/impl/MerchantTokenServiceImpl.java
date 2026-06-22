package com.markbay.subscription_engine.auth.token.service.impl;

import com.markbay.subscription_engine.auth.token.entity.MerchantRefreshToken;
import com.markbay.subscription_engine.auth.token.enums.TokenType;
import com.markbay.subscription_engine.auth.token.repository.MerchantRefreshTokenRepository;
import com.markbay.subscription_engine.auth.token.service.MerchantTokenService;
import com.markbay.subscription_engine.common.exception.InvalidCredentialException;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class MerchantTokenServiceImpl implements MerchantTokenService {

    private final MerchantRefreshTokenRepository tokenRepository;
    private final JwtService jwtService;

    @Override
    @Transactional
    public void saveRefreshToken(MerchantUser merchantUser, String refreshToken) {
        Jwt jwt = jwtService.decodeJwt(refreshToken);

        String tokenType = jwt.getClaimAsString("tokenType");
        if (!"REFRESH".equals(tokenType)) {
            throw new InvalidCredentialException("Invalid refresh token type");
        }

        String tokenId = jwt.getClaimAsString("tokenId");
        if (tokenId == null || tokenId.isBlank()) {
            throw new InvalidCredentialException("Invalid refresh token: tokenId missing");
        }

        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();

        if (expiresAt == null) {
            throw new InvalidCredentialException("Invalid refresh token: expiry missing");
        }

        MerchantRefreshToken token = MerchantRefreshToken.builder()
                .merchantUser(merchantUser)
                .tokenId(tokenId)
                .tokenType(TokenType.BEARER)
                .createdAt(issuedAt != null ? issuedAt : Instant.now())
                .expiresAt(expiresAt)
                .revoked(false)
                .expired(false)
                .build();

        tokenRepository.save(token);
    }

    @Override
    @Transactional
    public void revokeAllUserRefreshTokens(MerchantUser merchantUser) {
        List<MerchantRefreshToken> validTokens =
                tokenRepository.findAllValidTokensByMerchantUserId(merchantUser.getId());

        if (validTokens.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        validTokens.forEach(token -> {
            token.setRevoked(true);
            token.setExpired(true);
            token.setRevokedAt(now);
        });

        tokenRepository.saveAll(validTokens);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateRefreshToken(String refreshToken, MerchantUser merchantUser) {
        Jwt jwt = jwtService.decodeJwt(refreshToken);

        String tokenType = jwt.getClaimAsString("tokenType");
        if (!"REFRESH".equals(tokenType)) {
            throw new InvalidCredentialException("Invalid token type");
        }

        String tokenId = jwt.getClaimAsString("tokenId");
        if (tokenId == null || tokenId.isBlank()) {
            throw new InvalidCredentialException("Invalid refresh token: tokenId missing");
        }

        String subjectEmail = jwt.getSubject();
        if (subjectEmail == null || !subjectEmail.equalsIgnoreCase(merchantUser.getEmail())) {
            throw new InvalidCredentialException("Refresh token does not belong to this user");
        }

        String tokenTenantId = jwt.getClaimAsString("tenantId");
        if (tokenTenantId == null ||
                !tokenTenantId.equals(merchantUser.getTenant().getId().toString())) {
            throw new InvalidCredentialException("Refresh token tenant mismatch");
        }

        Long tokenSessionVersion = jwt.getClaim("sessionVersion");
        if (tokenSessionVersion == null ||
                !tokenSessionVersion.equals(merchantUser.getSessionVersion())) {
            throw new InvalidCredentialException("Invalid or expired session");
        }

        MerchantRefreshToken savedToken = tokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new InvalidCredentialException("Refresh token not found"));

        if (savedToken.isExpired() || savedToken.isRevoked()) {
            throw new InvalidCredentialException("Refresh token is no longer valid");
        }

        if (savedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialException("Refresh token has expired");
        }
    }

    @Override
    public String getTokenIdFromRefreshToken(String refreshToken) {
        Jwt jwt = jwtService.decodeJwt(refreshToken);

        String tokenId = jwt.getClaimAsString("tokenId");
        if (tokenId == null || tokenId.isBlank()) {
            throw new InvalidCredentialException("Invalid refresh token: tokenId missing");
        }

        return tokenId;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        String tokenId = getTokenIdFromRefreshToken(refreshToken);

        MerchantRefreshToken savedToken = tokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new InvalidCredentialException("Refresh token not found"));

        savedToken.setRevoked(true);
        savedToken.setExpired(true);
        savedToken.setRevokedAt(Instant.now());

        tokenRepository.save(savedToken);
    }

    @Scheduled(fixedRate = 1000L * 60 * 60 * 24) // every 24 hours
    @Transactional
    public void deleteExpiredTokens() {
        Instant now = Instant.now();
        log.info("Cleaning up expired/revoked merchant refresh tokens at {}", now);

        tokenRepository.deleteAllExpiredOrRevokedSince(now);

        log.info("Refresh token cleanup completed at {}", Instant.now());
    }
}