package com.markbay.subscription_engine.auth.dto;


import com.markbay.subscription_engine.merchant.entity.MerchantUser;

import java.util.UUID;

public record MerchantUserDto(
        UUID id,
        UUID tenantId,
        String firstName,
        String lastName,
        String email,
        String role
) {

    public static MerchantUserDto from(MerchantUser merchantUser) {
        return new MerchantUserDto(
                merchantUser.getId(),
                merchantUser.getTenant().getId(),
                merchantUser.getFirstName(),
                merchantUser.getLastName(),
                merchantUser.getEmail(),
                merchantUser.getRole().name()
        );
    }
}