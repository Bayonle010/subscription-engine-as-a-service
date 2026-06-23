package com.markbay.subscription_engine.apiKey.repository;


import com.markbay.subscription_engine.apiKey.entity.ApiKey;
import com.markbay.subscription_engine.apiKey.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ApiKey> findByTenantIdAndClientIdAndStatus(
            UUID tenantId,
            String clientId,
            ApiKeyStatus status
    );

    boolean existsByClientId(String clientId);
}