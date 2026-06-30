package com.markbay.subscription_engine.renewal.scheduler;


import com.markbay.subscription_engine.renewal.service.RenewalBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenewalBillingScheduler {

    private final RenewalBillingService renewalBillingService;

    @Value("${renewal.processor.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${renewal.processor.fixed-delay-ms:60000}")
    public void processRenewals() {
        List<UUID> dueSubscriptionIds =
                renewalBillingService.findDueRenewalSubscriptionIds(batchSize);

        if (dueSubscriptionIds.isEmpty()) {
            return;
        }

        log.info(
                "Processing due subscription renewals. count={}",
                dueSubscriptionIds.size()
        );

        for (UUID subscriptionId : dueSubscriptionIds) {
            try {
                renewalBillingService.processSubscriptionRenewal(subscriptionId);
            } catch (Exception exception) {
                log.error(
                        "Renewal processing failed. subscriptionId={}, reason={}",
                        subscriptionId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}