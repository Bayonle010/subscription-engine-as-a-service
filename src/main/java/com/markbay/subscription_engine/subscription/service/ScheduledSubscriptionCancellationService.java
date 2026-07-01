package com.markbay.subscription_engine.subscription.service;

import java.util.List;
import java.util.UUID;

public interface ScheduledSubscriptionCancellationService {

    List<UUID> findDueScheduledCancellationIds(int batchSize);

    void processScheduledCancellation(UUID subscriptionId);
}