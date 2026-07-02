package com.markbay.subscription_engine.subscription.service.impl;

import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import com.markbay.subscription_engine.subscription.service.ScheduledSubscriptionCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledSubscriptionCancellationServiceImpl
        implements ScheduledSubscriptionCancellationService {

    private final SubscriptionRepository subscriptionRepository;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueScheduledCancellationIds(int batchSize) {
        return subscriptionRepository.findDueScheduledCancellationIds(
                List.of(
                        SubscriptionStatus.ACTIVE,
                        SubscriptionStatus.TRIALING
                ),
                Instant.now(),
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional
    public void processScheduledCancellation(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository
                .findByIdForScheduledCancellationUpdate(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (!isDueForScheduledCancellation(subscription)) {
            log.info(
                    "Skipping scheduled cancellation because subscription is no longer due. subscriptionId={}, status={}, cancelAtPeriodEnd={}, currentPeriodEnd={}",
                    subscription.getId(),
                    subscription.getStatus(),
                    subscription.isCancelAtPeriodEnd(),
                    subscription.getCurrentPeriodEnd()
            );

            return;
        }

        Instant now = Instant.now();

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(now);

        recordSubscriptionCancelledEvent(subscription);

        log.info(
                "Subscription cancelled at period end. tenantId={}, customerId={}, subscriptionId={}, cancelledAt={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                now
        );
    }

    private boolean isDueForScheduledCancellation(Subscription subscription) {
        return subscription != null
                && (
                subscription.getStatus() == SubscriptionStatus.ACTIVE
                        || subscription.getStatus() == SubscriptionStatus.TRIALING
        )
                && subscription.isCancelAtPeriodEnd()
                && subscription.getCurrentPeriodEnd() != null
                && !subscription.getCurrentPeriodEnd().isAfter(Instant.now());
    }

    private void recordSubscriptionCancelledEvent(Subscription subscription) {
        Map<String, String> payload = buildPayload(subscription);

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_CANCELLED)
                        .eventReference("subscription.cancelled:" + subscription.getId())
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private Map<String, String> buildPayload(Subscription subscription) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("customerName", buildCustomerName(
                subscription.getCustomer().getFirstName(),
                subscription.getCustomer().getLastName()
        ));
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("subscriptionStatus", subscription.getStatus().name());
        payload.put("planId", subscription.getPlan().getId().toString());
        payload.put("planName", subscription.getPlan().getName());
        payload.put("amount", subscription.getAmount().toPlainString());
        payload.put("currency", subscription.getCurrency());
        payload.put("cancelAtPeriodEnd", Boolean.toString(subscription.isCancelAtPeriodEnd()));
        payload.put("currentPeriodStart", safeInstant(subscription.getCurrentPeriodStart()));
        payload.put("currentPeriodEnd", safeInstant(subscription.getCurrentPeriodEnd()));
        payload.put("cancelledAt", safeInstant(subscription.getCancelledAt()));
        payload.put("action", "PERIOD_END_CANCELLATION");
        payload.put("message", "Subscription cancelled at the end of the billing period");

        return payload;
    }

    private String buildCustomerName(
            String firstName,
            String lastName
    ) {
        String fullName = (
                safe(firstName) + " " + safe(lastName)
        ).trim();

        if (fullName.isBlank()) {
            return "there";
        }

        return fullName;
    }

    private String safeInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}