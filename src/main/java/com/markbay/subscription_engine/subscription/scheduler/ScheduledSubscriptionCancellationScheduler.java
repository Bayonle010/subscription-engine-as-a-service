package com.markbay.subscription_engine.subscription.scheduler;

import com.markbay.subscription_engine.subscription.service.ScheduledSubscriptionCancellationService;
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
public class ScheduledSubscriptionCancellationScheduler {

    private final ScheduledSubscriptionCancellationService scheduledCancellationService;

    @Value("${subscription.cancellation-processor.batch-size:25}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${subscription.cancellation-processor.fixed-delay-ms:60000}"
    )
    public void processScheduledCancellations() {
        List<UUID> dueSubscriptionIds =
                scheduledCancellationService.findDueScheduledCancellationIds(batchSize);

        if (dueSubscriptionIds.isEmpty()) {
            return;
        }

        log.info(
                "Processing scheduled subscription cancellations. count={}",
                dueSubscriptionIds.size()
        );

        for (UUID subscriptionId : dueSubscriptionIds) {
            try {
                scheduledCancellationService.processScheduledCancellation(subscriptionId);
            } catch (Exception exception) {
                log.error(
                        "Scheduled subscription cancellation failed. subscriptionId={}, reason={}",
                        subscriptionId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}