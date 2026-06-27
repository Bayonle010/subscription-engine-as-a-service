package com.markbay.subscription_engine.webhook.listener;

import com.markbay.subscription_engine.webhook.event.InboundWebhookReceivedEvent;
import com.markbay.subscription_engine.webhook.service.NombaWebhookProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NombaWebhookProcessingListener {

    private final NombaWebhookProcessorService webhookProcessorService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInboundWebhookReceived(InboundWebhookReceivedEvent event) {
        log.info(
                "Starting async inbound webhook processing. eventId={}",
                event.inboundWebhookEventId()
        );

        webhookProcessorService.processWebhookEvent(
                event.inboundWebhookEventId()
        );
    }
}