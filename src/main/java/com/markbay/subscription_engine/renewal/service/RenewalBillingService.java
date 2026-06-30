package com.markbay.subscription_engine.renewal.service;

import java.util.List;
import java.util.UUID;

public interface RenewalBillingService {

    List<UUID> findDueRenewalSubscriptionIds(int batchSize);

    void processSubscriptionRenewal(UUID subscriptionId);
}