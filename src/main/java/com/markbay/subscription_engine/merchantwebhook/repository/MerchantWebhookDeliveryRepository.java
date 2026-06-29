package com.markbay.subscription_engine.merchantwebhook.repository;

import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookDelivery;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookDeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantWebhookDeliveryRepository
        extends JpaRepository<MerchantWebhookDelivery, UUID> {

    @EntityGraph(attributePaths = {
            "tenant",
            "endpoint",
            "outboxEvent"
    })
    Optional<MerchantWebhookDelivery> findByEndpoint_IdAndOutboxEvent_Id(
            UUID endpointId,
            UUID outboxEventId
    );

    @EntityGraph(attributePaths = {
            "tenant",
            "endpoint",
            "outboxEvent"
    })
    List<MerchantWebhookDelivery> findAllByOutboxEvent_IdOrderByCreatedAtDesc(
            UUID outboxEventId
    );

    @EntityGraph(attributePaths = {
            "tenant",
            "endpoint",
            "outboxEvent"
    })
    List<MerchantWebhookDelivery> findAllByTenant_IdOrderByCreatedAtDesc(
            UUID tenantId
    );

    @EntityGraph(attributePaths = {
            "tenant",
            "endpoint",
            "outboxEvent"
    })
    List<MerchantWebhookDelivery> findAllByTenant_IdAndStatusOrderByCreatedAtDesc(
            UUID tenantId,
            MerchantWebhookDeliveryStatus status
    );

    boolean existsByEndpoint_IdAndOutboxEvent_Id(
            UUID endpointId,
            UUID outboxEventId
    );
}