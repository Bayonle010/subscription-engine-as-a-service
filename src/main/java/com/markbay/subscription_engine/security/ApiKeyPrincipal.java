package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.apiKey.entitty.ApiKey;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ApiKeyPrincipal {

    private final UUID apiKeyId;
    private final UUID tenantId;
    private final String accountId;
    private final String clientId;
    private final String mode;

    public ApiKeyPrincipal(ApiKey apiKey) {
        this.apiKeyId = apiKey.getId();
        this.tenantId = apiKey.getTenant().getId();
        this.accountId = apiKey.getTenant().getId().toString();
        this.clientId = apiKey.getClientId();
        this.mode = apiKey.getMode().name();
    }
}