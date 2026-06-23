package com.markbay.subscription_engine.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
public class AuthenticatedMerchantProvider {

    public MerchantPrincipal getCurrentMerchant() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof MerchantPrincipal principal)) {
            throw new AccessDeniedException("Merchant authentication is required");
        }

        return principal;
    }

    public UUID getCurrentTenantId() {
        return getCurrentMerchant().getTenantId();
    }

    public UUID getCurrentUserId() {
        return getCurrentMerchant().getId();
    }
}
