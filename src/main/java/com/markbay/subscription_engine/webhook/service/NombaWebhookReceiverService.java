package com.markbay.subscription_engine.webhook.service;

public interface NombaWebhookReceiverService {

    void receiveWebhook(
            String rawPayload,
            String signature,
            String algorithm,
            String version,
            String timestamp
    );
}