package com.markbay.subscription_engine.auth.service.impl;

import com.markbay.subscription_engine.auth.dto.AuthResponse;
import com.markbay.subscription_engine.auth.dto.LoginRequest;
import com.markbay.subscription_engine.auth.dto.MerchantUserDto;
import com.markbay.subscription_engine.auth.dto.RegisterMerchantRequest;
import com.markbay.subscription_engine.auth.service.AuthService;
import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.enums.MerchantRole;
import com.markbay.subscription_engine.merchant.enums.MerchantUserStatus;
import com.markbay.subscription_engine.merchant.repository.MerchantUserRepository;
import com.markbay.subscription_engine.security.JwtService;
import com.markbay.subscription_engine.tenant.TenantStatus;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {

    private final TenantRepository tenantRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public AuthResponse register(RegisterMerchantRequest request) {
        if (tenantRepository.existsByBusinessEmailIgnoreCase(request.businessEmail())) {
            throw new BadRequestException("Business email is already registered");
        }

        if (merchantUserRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("User email is already registered");
        }

        Tenant tenant = Tenant.builder()
                .businessName(request.businessName().trim())
                .businessEmail(request.businessEmail().trim().toLowerCase())
                .supportEmail(request.businessEmail().trim().toLowerCase())
                .defaultCurrency("NGN")
                .billingTimezone("Africa/Lagos")
                .status(TenantStatus.ACTIVE)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        MerchantUser merchantUser = MerchantUser.builder()
                .tenant(savedTenant)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(MerchantRole.OWNER)
                .status(MerchantUserStatus.ACTIVE)
                .sessionVersion(0L)
                .build();

        MerchantUser savedUser = merchantUserRepository.save(merchantUser);

        String accessToken = jwtService.generateAccessToken(savedUser);

        return new AuthResponse(
                accessToken,
                "Bearer",
                savedTenant.getId(),
                MerchantUserDto.from(savedUser)
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(),
                            request.password()
                    )
            );
        } catch (Exception exception) {
            throw new BadCredentialsException("Invalid email or password");
        }

        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(
                request.email().trim().toLowerCase()
        ).orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        merchantUser.setLastLoginAt(Instant.now());
        MerchantUser savedUser = merchantUserRepository.save(merchantUser);

        String accessToken = jwtService.generateAccessToken(savedUser);

        return new AuthResponse(
                accessToken,
                "Bearer",
                savedUser.getTenant().getId(),
                MerchantUserDto.from(savedUser)
        );
    }

}

