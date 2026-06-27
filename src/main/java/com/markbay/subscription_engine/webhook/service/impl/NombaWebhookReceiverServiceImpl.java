package com.markbay.subscription_engine.webhook.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.InvalidCredentialException;
import com.markbay.subscription_engine.nomba.support.NombaWebhookPayloadExtractor;
import com.markbay.subscription_engine.nomba.support.NombaWebhookSignatureVerifier;
import com.markbay.subscription_engine.webhook.entity.InboundWebhookEvent;
import com.markbay.subscription_engine.webhook.enums.InboundWebhookEventStatus;
import com.markbay.subscription_engine.webhook.enums.WebhookProvider;
import com.markbay.subscription_engine.webhook.event.InboundWebhookReceivedEvent;
import com.markbay.subscription_engine.webhook.repository.InboundWebhookEventRepository;
import com.markbay.subscription_engine.webhook.service.NombaWebhookReceiverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class NombaWebhookReceiverServiceImpl implements NombaWebhookReceiverService {

    private final NombaWebhookSignatureVerifier signatureVerifier;
    private final NombaWebhookPayloadExtractor payloadExtractor;
    private final InboundWebhookEventRepository inboundWebhookEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void receiveWebhook(
            String rawPayload,
            String signature,
            String algorithm,
            String version,
            String timestamp
    ) {
        boolean valid = signatureVerifier.isValid(
                rawPayload,
                signature,
                algorithm,
                version,
                timestamp
        );

        if (!valid) {
            throw new InvalidCredentialException("Invalid Nomba webhook signature");
        }

        JsonNode payload = payloadExtractor.readTree(rawPayload);

        String eventType = payloadExtractor.eventType(payload);
        String eventReference = payloadExtractor.eventReference(payload);

        if (!hasText(eventType)) {
            throw new BadRequestException("Nomba webhook event type is missing");
        }

        if (!hasText(eventReference)) {
            throw new BadRequestException("Nomba webhook event reference is missing");
        }

        try {
            InboundWebhookEvent webhookEvent = InboundWebhookEvent.builder()
                    .provider(WebhookProvider.NOMBA)
                    .eventType(eventType)
                    .eventReference(eventReference)
                    .payload(rawPayload)
                    .signature(signature)
                    .timestampHeader(timestamp)
                    .status(InboundWebhookEventStatus.RECEIVED)
                    .build();

            InboundWebhookEvent savedEvent =
                    inboundWebhookEventRepository.save(webhookEvent);

            eventPublisher.publishEvent(
                    new InboundWebhookReceivedEvent(savedEvent.getId())
            );

            log.info(
                    "Nomba webhook saved and queued. eventId={}, eventType={}, eventReference={}",
                    savedEvent.getId(),
                    savedEvent.getEventType(),
                    savedEvent.getEventReference()
            );

        } catch (DataIntegrityViolationException exception) {
            log.info(
                    "Duplicate Nomba webhook ignored. eventType={}, eventReference={}",
                    eventType,
                    eventReference
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}