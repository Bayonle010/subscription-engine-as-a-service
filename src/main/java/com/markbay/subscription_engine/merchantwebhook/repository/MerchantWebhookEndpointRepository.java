package com.markbay.subscription_engine.merchantwebhook.repository;

import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookEndpointStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantWebhookEndpointRepository
        extends JpaRepository<MerchantWebhookEndpoint, UUID> {

    @EntityGraph(attributePaths = {"tenant", "subscribedEvents"})
    List<MerchantWebhookEndpoint> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "subscribedEvents"})
    List<MerchantWebhookEndpoint> findAllByTenant_IdAndStatus(
            UUID tenantId,
            MerchantWebhookEndpointStatus status
    );

    @EntityGraph(attributePaths = {"tenant", "subscribedEvents"})
    Optional<MerchantWebhookEndpoint> findByIdAndTenant_Id(
            UUID endpointId,
            UUID tenantId
    );
}