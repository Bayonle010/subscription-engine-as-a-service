package com.markbay.subscription_engine.webhook.service;

import java.util.UUID;

public interface NombaWebhookProcessorService {

    void processWebhookEvent(UUID eventId);
}