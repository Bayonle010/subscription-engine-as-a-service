package com.markbay.subscription_engine.security;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthenticationIdentity {

    private UUID userId;
    private UUID tenantId;
    private String email;
    private String role;
}