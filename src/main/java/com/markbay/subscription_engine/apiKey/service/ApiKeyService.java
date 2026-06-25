package com.markbay.subscription_engine.apiKey.service;

import com.markbay.subscription_engine.apiKey.dto.ApiKeyResponse;
import com.markbay.subscription_engine.apiKey.dto.CreateApiKeyRequest;
import com.markbay.subscription_engine.apiKey.entity.ApiKey;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {

    ApiKeyResponse createApiKey(CreateApiKeyRequest request);

    List<ApiKeyResponse> listApiKeys();

    ApiKeyResponse revokeApiKey(UUID apiKeyId);

    ApiKey authenticateApiKey(
            UUID accountId,
            String clientId,
            String secretKey
    );


}
