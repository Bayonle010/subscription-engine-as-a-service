package com.markbay.subscription_engine.reconciliation.secheduler;

import com.markbay.subscription_engine.reconciliation.config.PaymentReconciliationProperties;
import com.markbay.subscription_engine.reconciliation.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationProperties reconciliationProperties;
    private final PaymentReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${reconciliation.fixed-delay-ms:120000}")
    public void runPaymentReconciliation() {
        int batchSize = reconciliationProperties.getBatchSize();

        reconcileSubscriptionCheckouts(batchSize);
        reconcilePaymentRescueCheckouts(batchSize);
        retryFailedWebhooks(batchSize);
    }

    private void reconcileSubscriptionCheckouts(int batchSize) {
        List<UUID> sessionIds =
                reconciliationService.findDueSubscriptionCheckoutSessionIds(batchSize);

        if (sessionIds.isEmpty()) {
            return;
        }

        log.info(
                "Reconciling pending subscription checkout sessions. count={}",
                sessionIds.size()
        );

        for (UUID sessionId : sessionIds) {
            try {
                reconciliationService.reconcileSubscriptionCheckoutSession(sessionId);
            } catch (Exception exception) {
                log.error(
                        "Subscription checkout reconciliation failed. sessionId={}, reason={}",
                        sessionId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    private void reconcilePaymentRescueCheckouts(int batchSize) {
        List<UUID> sessionIds =
                reconciliationService.findDuePaymentRescueCheckoutSessionIds(batchSize);

        if (sessionIds.isEmpty()) {
            return;
        }

        log.info(
                "Reconciling pending payment rescue checkout sessions. count={}",
                sessionIds.size()
        );

        for (UUID sessionId : sessionIds) {
            try {
                reconciliationService.reconcilePaymentRescueCheckoutSession(sessionId);
            } catch (Exception exception) {
                log.error(
                        "Payment rescue checkout reconciliation failed. sessionId={}, reason={}",
                        sessionId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    private void retryFailedWebhooks(int batchSize) {
        if (!reconciliationProperties.isRetryFailedWebhooks()) {
            return;
        }

        List<UUID> eventIds =
                reconciliationService.findFailedWebhookEventIds(batchSize);

        if (eventIds.isEmpty()) {
            return;
        }

        log.info(
                "Retrying failed inbound webhook events. count={}",
                eventIds.size()
        );

        for (UUID eventId : eventIds) {
            try {
                reconciliationService.retryFailedWebhookEvent(eventId);
            } catch (Exception exception) {
                log.error(
                        "Failed inbound webhook retry failed. eventId={}, reason={}",
                        eventId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}