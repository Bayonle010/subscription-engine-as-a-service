package com.markbay.subscription_engine.reconciliation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation")
public class PaymentReconciliationProperties {

    private long fixedDelayMs = 120000;

    private int batchSize = 25;

    private int pendingMinAgeMinutes = 5;

    private int pendingExpireAfterHours = 24;

    private boolean retryFailedWebhooks = true;

    private int failedWebhookRetryAgeMinutes = 2;
}