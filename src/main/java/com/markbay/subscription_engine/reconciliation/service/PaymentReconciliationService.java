package com.markbay.subscription_engine.reconciliation.service;

import java.util.List;
import java.util.UUID;

public interface PaymentReconciliationService {

    List<UUID> findDueSubscriptionCheckoutSessionIds(int batchSize);

    List<UUID> findDuePaymentRescueCheckoutSessionIds(int batchSize);

    List<UUID> findFailedWebhookEventIds(int batchSize);

    void reconcileSubscriptionCheckoutSession(UUID sessionId);

    void reconcilePaymentRescueCheckoutSession(UUID sessionId);

    void retryFailedWebhookEvent(UUID eventId);
}