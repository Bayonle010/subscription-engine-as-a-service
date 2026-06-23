package com.markbay.subscription_engine.apiKey.service.impl;

import com.markbay.subscription_engine.apiKey.dto.ApiKeyResponse;
import com.markbay.subscription_engine.apiKey.dto.CreateApiKeyRequest;
import com.markbay.subscription_engine.apiKey.entitty.ApiKey;
import com.markbay.subscription_engine.apiKey.enums.ApiKeyMode;
import com.markbay.subscription_engine.apiKey.enums.ApiKeyStatus;
import com.markbay.subscription_engine.apiKey.repository.ApiKeyRepository;
import com.markbay.subscription_engine.apiKey.service.ApiKeyService;
import com.markbay.subscription_engine.apiKey.util.ApiCredentialGenerator;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.repository.MerchantUserRepository;
import com.markbay.subscription_engine.security.AuthenticatedMerchantProvider;
import com.markbay.subscription_engine.security.MerchantPrincipal;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedMerchantProvider merchantProvider;



    @Transactional
    @Override
    public ApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        MerchantPrincipal currentMerchant = merchantProvider.getCurrentMerchant();

        Tenant tenant = tenantRepository.findById(currentMerchant.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        MerchantUser createdBy = merchantUserRepository.findById(currentMerchant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant user not found"));

        ApiKeyMode mode = request.mode();

        String clientId = generateUniqueClientId(mode);
        String secretKey = ApiCredentialGenerator.generateSecretKey(mode);

        ApiKey apiKey = ApiKey.builder()
                .tenant(tenant)
                .createdBy(createdBy)
                .name(request.name().trim())
                .clientId(clientId)
                .secretHash(passwordEncoder.encode(secretKey))
                .secretPreview(ApiCredentialGenerator.previewSecret(secretKey))
                .mode(mode)
                .status(ApiKeyStatus.ACTIVE)
                .build();

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        return ApiKeyResponse.from(savedApiKey, secretKey);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ApiKeyResponse> listApiKeys() {
        UUID tenantId = merchantProvider.getCurrentTenantId();

        return apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(ApiKeyResponse::withoutSecret)
                .toList();

    }

    @Transactional
    @Override
    public ApiKeyResponse revokeApiKey(UUID apiKeyId) {
        UUID tenantId = merchantProvider.getCurrentTenantId();

        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        if (apiKey.getStatus() == ApiKeyStatus.REVOKED) {
            return ApiKeyResponse.withoutSecret(apiKey);
        }

        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        return ApiKeyResponse.withoutSecret(savedApiKey);
    }

    @Transactional
    @Override
    public ApiKey authenticateApiKey(UUID accountId, String clientId, String secretKey) {
        ApiKey apiKey = apiKeyRepository
                .findByTenantIdAndClientIdAndStatus(accountId, clientId, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Invalid API credentials"));

        boolean secretMatches = passwordEncoder.matches(secretKey, apiKey.getSecretHash());

        if (!secretMatches) {
            throw new BadCredentialsException("Invalid API credentials");
        }

        apiKey.setLastUsedAt(Instant.now());
        return apiKeyRepository.save(apiKey);
    }

    private String generateUniqueClientId(ApiKeyMode mode) {
        String clientId;

        do {
            clientId = ApiCredentialGenerator.generateClientId(mode);
        } while (apiKeyRepository.existsByClientId(clientId));

        return clientId;
    }
}
