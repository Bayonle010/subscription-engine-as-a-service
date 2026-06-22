package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class JwtService {

    private static final String ISSUER = "subscription-engine";
    private static final long ACCESS_TOKEN_DURATION_IN_SECONDS = 60 * 60L; // 1 hour

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public String generateAccessToken(MerchantUser merchantUser) {
        Instant now = Instant.now();

        List<String> roles = List.of("ROLE_" + merchantUser.getRole().name());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_DURATION_IN_SECONDS))
                .subject(merchantUser.getEmail())
                .claim("userId", merchantUser.getId().toString())
                .claim("tenantId", merchantUser.getTenant().getId().toString())
                .claim("role", merchantUser.getRole().name())
                .claim("roles", roles)
                .claim("tokenType", "ACCESS")
                .claim("sessionVersion", merchantUser.getSessionVersion())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public Jwt decodeJwt(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new BadJwtException("Invalid or expired JWT token");
        }
    }

    public AuthenticationIdentity buildAuthenticationIdentity(Jwt jwt) {
        return AuthenticationIdentity.builder()
                .userId(UUID.fromString(jwt.getClaimAsString("userId")))
                .tenantId(UUID.fromString(jwt.getClaimAsString("tenantId")))
                .email(jwt.getSubject())
                .role(jwt.getClaimAsString("role"))
                .build();
    }

    public List<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .map(role -> (GrantedAuthority) () -> role)
                .toList();
    }
}