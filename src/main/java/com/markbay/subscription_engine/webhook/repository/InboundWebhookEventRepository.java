package com.markbay.subscription_engine.webhook.repository;

import com.markbay.subscription_engine.webhook.entity.InboundWebhookEvent;
import com.markbay.subscription_engine.webhook.enums.WebhookProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InboundWebhookEventRepository
        extends JpaRepository<InboundWebhookEvent, UUID> {

    Optional<InboundWebhookEvent> findByProviderAndEventReference(
            WebhookProvider provider,
            String eventReference
    );
}