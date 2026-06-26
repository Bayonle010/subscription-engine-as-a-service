package com.markbay.subscription_engine.auth.service.impl;

import com.markbay.subscription_engine.apiKey.entity.ApiKey;
import com.markbay.subscription_engine.apiKey.service.ApiKeyService;
import com.markbay.subscription_engine.auth.dto.*;
import com.markbay.subscription_engine.auth.service.AuthService;
import com.markbay.subscription_engine.auth.token.service.MerchantTokenService;
import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.InvalidCredentialException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.merchant.enums.MerchantRole;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.repository.MerchantUserRepository;
import com.markbay.subscription_engine.merchant.enums.MerchantUserStatus;
import com.markbay.subscription_engine.security.AuthenticatedMerchantProvider;
import com.markbay.subscription_engine.security.JwtService;
import com.markbay.subscription_engine.security.MerchantPrincipal;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.event.TenantRegisteredEvent;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import com.markbay.subscription_engine.tenant.enums.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {

    private final TenantRepository tenantRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MerchantTokenService tokenService;
    private final AuthenticatedMerchantProvider merchantProvider;
    private final ApiKeyService apiKeyService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AuthResponse register(RegisterMerchantRequest request) {
        String businessEmail = request.businessEmail().trim().toLowerCase();
        String userEmail = request.email().trim().toLowerCase();

        if (tenantRepository.existsByBusinessEmailIgnoreCase(businessEmail)) {
            throw new ConflictException("Business email is already registered");
        }

        if (merchantUserRepository.existsByEmailIgnoreCase(userEmail)) {
            throw new ConflictException("User email is already registered");
        }

        Tenant tenant = Tenant.builder()
                .businessName(request.businessName().trim())
                .businessEmail(businessEmail)
                .supportEmail(businessEmail)
                .defaultCurrency("NGN")
                .billingTimezone("Africa/Lagos")
                .status(TenantStatus.ACTIVE)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        MerchantUser merchantUser = MerchantUser.builder()
                .tenant(savedTenant)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(userEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(MerchantRole.OWNER)
                .status(MerchantUserStatus.ACTIVE)
                .sessionVersion(0L)
                .build();

        MerchantUser savedUser = merchantUserRepository.save(merchantUser);

        eventPublisher.publishEvent(new TenantRegisteredEvent(savedTenant.getId()));

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        tokenService.saveRefreshToken(savedUser, refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                savedTenant.getId(),
                MerchantUserDto.from(savedUser)
        );
    }


    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.password()
                    )
            );
        } catch (Exception exception) {
            throw new BadCredentialsException("Invalid email or password");
        }

        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        merchantUser.setLastLoginAt(Instant.now());
        MerchantUser savedUser = merchantUserRepository.save(merchantUser);

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        tokenService.saveRefreshToken(savedUser, refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                savedUser.getTenant().getId(),
                MerchantUserDto.from(savedUser)
        );
    }


    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        Jwt jwt = jwtService.decodeJwt(refreshToken);
        String email = jwt.getSubject();

        if (email == null || email.isBlank()) {
            throw new InvalidCredentialException("Invalid refresh token");
        }

        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant user not found"));

        tokenService.validateRefreshToken(refreshToken, merchantUser);

        /*
         * Refresh token rotation:
         * Revoke old refresh token and issue a new access token + new refresh token.
         */
        tokenService.revokeRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(merchantUser);
        String newRefreshToken = jwtService.generateRefreshToken(merchantUser);

        tokenService.saveRefreshToken(merchantUser, newRefreshToken);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                merchantUser.getTenant().getId(),
                MerchantUserDto.from(merchantUser)
        );
    }


    @Override
    @Transactional
    public void logout(String refreshToken) {
        Jwt jwt = jwtService.decodeJwt(refreshToken);

        String email = jwt.getSubject();

        if (email == null || email.isBlank()) {
            throw new InvalidCredentialException("Invalid refresh token");
        }

        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialException("Invalid refresh token"));

        /*
         * This invalidates all access and refresh tokens with older sessionVersion.
         */
        merchantUser.setSessionVersion(merchantUser.getSessionVersion() + 1);
        merchantUserRepository.save(merchantUser);

        tokenService.revokeAllUserRefreshTokens(merchantUser);
    }

    @Override
    public MerchantUserDto getAuthenticatedUser() {
        MerchantPrincipal currentMerchant = merchantProvider.getCurrentMerchant();

        MerchantUser merchantUser = merchantUserRepository.findById(currentMerchant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant user not found"));

        return MerchantUserDto.from(merchantUser);
    }

    @Override
    @Transactional
    public ApiAccessTokenResponse generateApiAccessToken(
            String accountIdHeader,
            String clientId,
            String secretKey
    ) {
        if (!hasText(accountIdHeader)) {
            throw new BadRequestException("accountId header is required");
        }

        if (!hasText(clientId)) {
            throw new BadRequestException("clientId header is required");
        }

        if (!hasText(secretKey)) {
            throw new BadRequestException("secretKey header is required");
        }

        UUID accountId;

        try {
            accountId = UUID.fromString(accountIdHeader.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("accountId must be a valid UUID");
        }

        ApiKey apiKey = apiKeyService.authenticateApiKey(
                accountId,
                clientId.trim(),
                secretKey.trim()
        );

        String accessToken = jwtService.generateApiAccessToken(apiKey);

        return ApiAccessTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getApiAccessTokenExpiresInSeconds())
                .accountId(apiKey.getTenant().getId())
                .clientId(apiKey.getClientId())
                .mode(apiKey.getMode().name())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}