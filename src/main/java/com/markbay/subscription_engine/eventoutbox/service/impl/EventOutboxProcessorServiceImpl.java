package com.markbay.subscription_engine.eventoutbox.service.impl;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxStatus;
import com.markbay.subscription_engine.eventoutbox.repository.EventOutboxRepository;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxProcessorService;
import com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookDeliveryService;
import com.markbay.subscription_engine.notification.email.service.OutboxEmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventOutboxProcessorServiceImpl implements EventOutboxProcessorService {

    private final EventOutboxRepository eventOutboxRepository;
    private final MerchantWebhookDeliveryService merchantWebhookDeliveryService;
    private final OutboxEmailNotificationService outboxEmailNotificationService;

    @Override
    @Scheduled(fixedDelayString = "${event-outbox.processor.fixed-delay-ms:30000}")
    @Transactional
    public void processDueEvents() {
        List<EventOutbox> events = eventOutboxRepository.findDueEventsForUpdate(
                List.of(EventOutboxStatus.PENDING, EventOutboxStatus.FAILED),
                Instant.now(),
                PageRequest.of(0, 50)
        );

        if (events.isEmpty()) {
            return;
        }

        log.info("Processing due outbox events. count={}", events.size());

        for (EventOutbox event : events) {
            process(event);
        }
    }

    @Override
    @Transactional
    public void processEvent(UUID eventId) {
        EventOutbox event = eventOutboxRepository.findWithTenantById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found"));

        process(event);
    }

    private void process(EventOutbox event) {
        if (event.getStatus() == EventOutboxStatus.PROCESSED) {
            return;
        }

        try {
            event.setStatus(EventOutboxStatus.PROCESSING);
            event.setAttemptCount(event.getAttemptCount() + 1);

            merchantWebhookDeliveryService.dispatchEvent(event);

            outboxEmailNotificationService.handle(event);

            event.setStatus(EventOutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setLastError(null);

            log.info(
                    "Outbox event processed. eventId={}, eventType={}, eventReference={}",
                    event.getId(),
                    event.getEventType().value(),
                    event.getEventReference()
            );

        } catch (Exception exception) {
            event.setStatus(EventOutboxStatus.FAILED);
            event.setLastError(exception.getMessage());
            event.setNextAttemptAt(calculateNextAttemptAt(event.getAttemptCount()));

            if (event.getAttemptCount() >= event.getMaxAttempts()) {
                event.setStatus(EventOutboxStatus.DISCARDED);

                log.error(
                        "Outbox event discarded after max attempts. eventId={}, eventType={}, attempts={}, reason={}",
                        event.getId(),
                        event.getEventType().value(),
                        event.getAttemptCount(),
                        exception.getMessage(),
                        exception
                );

                return;
            }

            log.warn(
                    "Outbox event processing failed. eventId={}, eventType={}, attempt={}, nextAttemptAt={}, reason={}",
                    event.getId(),
                    event.getEventType().value(),
                    event.getAttemptCount(),
                    event.getNextAttemptAt(),
                    exception.getMessage()
            );
        }
    }

    private Instant calculateNextAttemptAt(int attemptCount) {
        long delaySeconds = Math.min(
                3600,
                (long) Math.pow(2, Math.max(attemptCount, 1)) * 30
        );

        return Instant.now().plus(Duration.ofSeconds(delaySeconds));
    }
}