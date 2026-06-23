package com.markbay.subscription_engine.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuthenticatedTenantProvider {

    public UUID getCurrentTenantId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof MerchantPrincipal merchantPrincipal) {
            return merchantPrincipal.getTenantId();
        }

        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal.getTenantId();
        }

        throw new AccessDeniedException("Invalid authentication principal");
    }

    public Optional<UUID> getCurrentMerchantUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof MerchantPrincipal merchantPrincipal) {
            return Optional.of(merchantPrincipal.getId());
        }

        return Optional.empty();
    }

    public boolean isApiClient() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return authentication != null &&
                authentication.getPrincipal() instanceof ApiKeyPrincipal;
    }
}