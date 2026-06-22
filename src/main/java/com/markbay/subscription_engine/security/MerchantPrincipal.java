package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.enums.MerchantUserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class MerchantPrincipal implements UserDetails {

    private final MerchantUser merchantUser;

    public MerchantPrincipal(MerchantUser merchantUser) {
        this.merchantUser = merchantUser;
    }

    public UUID getId() {
        return merchantUser.getId();
    }

    public UUID getTenantId() {
        return merchantUser.getTenant().getId();
    }

    public String getEmail() {
        return merchantUser.getEmail();
    }

    public String getRole() {
        return merchantUser.getRole().name();
    }

    public Long getSessionVersion() {
        return merchantUser.getSessionVersion();
    }

    public MerchantUser getMerchantUser() {
        return merchantUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + merchantUser.getRole().name());
    }

    @Override
    public String getPassword() {
        return merchantUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return merchantUser.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return merchantUser.getStatus() == MerchantUserStatus.ACTIVE;
    }

    @Override
    public boolean isAccountNonLocked() {
        return merchantUser.getStatus() == MerchantUserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return merchantUser.getStatus() == MerchantUserStatus.ACTIVE;
    }

    @Override
    public boolean isEnabled() {
        return merchantUser.getStatus() == MerchantUserStatus.ACTIVE;
    }
}