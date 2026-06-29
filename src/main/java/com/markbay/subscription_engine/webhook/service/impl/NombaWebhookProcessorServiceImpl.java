package com.markbay.subscription_engine.webhook.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.nomba.gateway.NombaTransactionGateway;
import com.markbay.subscription_engine.nomba.support.NombaWebhookPayloadExtractor;
import com.markbay.subscription_engine.subscription.service.SubscriptionActivationService;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
import com.markbay.subscription_engine.subscriptioncheckout.repository.SubscriptionCheckoutSessionRepository;
import com.markbay.subscription_engine.webhook.entity.InboundWebhookEvent;
import com.markbay.subscription_engine.webhook.enums.InboundWebhookEventStatus;
import com.markbay.subscription_engine.webhook.enums.NombaWebhookEventType;
import com.markbay.subscription_engine.webhook.repository.InboundWebhookEventRepository;
import com.markbay.subscription_engine.webhook.service.NombaWebhookProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NombaWebhookProcessorServiceImpl implements NombaWebhookProcessorService {

    private final InboundWebhookEventRepository inboundWebhookEventRepository;
    private final SubscriptionCheckoutSessionRepository checkoutSessionRepository;
    private final NombaWebhookPayloadExtractor payloadExtractor;
    private final NombaTransactionGateway nombaTransactionGateway;
    private final SubscriptionActivationService subscriptionActivationService;

    @Override
    @Transactional
    public void processWebhookEvent(UUID eventId) {
        InboundWebhookEvent event = inboundWebhookEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inbound webhook event not found"
                ));

        if (isAlreadyHandled(event)) {
            return;
        }

        try {
            event.setStatus(InboundWebhookEventStatus.PROCESSING);

            NombaWebhookEventType eventType =
                    NombaWebhookEventType.from(event.getEventType());

            if (eventType == null) {
                markIgnored(event);

                log.info(
                        "Nomba webhook ignored because event type is unsupported. eventId={}, eventType={}",
                        event.getId(),
                        event.getEventType()
                );

                return;
            }

            switch (eventType) {
                case PAYMENT_SUCCESS -> {
                    processPaymentSuccess(event);
                    markProcessed(event);
                }

                case PAYMENT_FAILED -> {
                    processPaymentFailed(event);
                    markProcessed(event);
                }

                default -> {
                    markIgnored(event);

                    log.info(
                            "Nomba webhook ignored. eventId={}, eventType={}",
                            event.getId(),
                            event.getEventType()
                    );
                }
            }

        } catch (Exception exception) {
            markFailed(event, exception);

            log.error(
                    "Nomba webhook processing failed. eventId={}, eventType={}, reason={}",
                    event.getId(),
                    event.getEventType(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void processPaymentSuccess(InboundWebhookEvent event) {
        JsonNode payload = payloadExtractor.readTree(event.getPayload());

        NombaWebhookPaymentData paymentData =
                payloadExtractor.paymentData(payload);

        String orderReference = paymentData.orderReference();

        if (!hasText(orderReference)) {
            throw new IllegalStateException("Nomba webhook order reference is missing");
        }

        SubscriptionCheckoutSession checkoutSession = checkoutSessionRepository
                .findByOrderReference(orderReference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription checkout session not found"
                ));

        if (checkoutSession.getStatus() == CheckoutSessionStatus.COMPLETED) {
            log.info(
                    "Checkout session already completed. checkoutSessionId={}, orderReference={}",
                    checkoutSession.getId(),
                    orderReference
            );

            return;
        }

        NombaVerifiedTransactionResult verifiedTransaction =
                nombaTransactionGateway.verifyByOrderReference(orderReference);

        subscriptionActivationService.activateFromSuccessfulCheckout(
                checkoutSession,
                verifiedTransaction,
                paymentData
        );
    }

    private void processPaymentFailed(InboundWebhookEvent event) {
        JsonNode payload = payloadExtractor.readTree(event.getPayload());

        String orderReference = payloadExtractor.orderReference(payload);

        if (!hasText(orderReference)) {
            log.warn(
                    "Payment failed webhook has no order reference. eventId={}",
                    event.getId()
            );

            return;
        }

        checkoutSessionRepository.findByOrderReference(orderReference)
                .ifPresentOrElse(
                        checkoutSession -> {
                            if (checkoutSession.getStatus() == CheckoutSessionStatus.COMPLETED) {
                                log.info(
                                        "Ignoring payment failed webhook because checkout is already completed. checkoutSessionId={}, orderReference={}",
                                        checkoutSession.getId(),
                                        orderReference
                                );

                                return;
                            }

                            checkoutSession.setStatus(CheckoutSessionStatus.FAILED);
                            checkoutSession.setFailedAt(Instant.now());

                            log.info(
                                    "Checkout session marked as failed. checkoutSessionId={}, orderReference={}",
                                    checkoutSession.getId(),
                                    orderReference
                            );
                        },
                        () -> log.warn(
                                "Payment failed webhook checkout session not found. orderReference={}",
                                orderReference
                        )
                );
    }

    private boolean isAlreadyHandled(InboundWebhookEvent event) {
        return event.getStatus() == InboundWebhookEventStatus.PROCESSED
                || event.getStatus() == InboundWebhookEventStatus.IGNORED;
    }

    private void markProcessed(InboundWebhookEvent event) {
        event.setStatus(InboundWebhookEventStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        event.setFailureReason(null);
    }

    private void markIgnored(InboundWebhookEvent event) {
        event.setStatus(InboundWebhookEventStatus.IGNORED);
        event.setProcessedAt(Instant.now());
        event.setFailureReason(null);
    }

    private void markFailed(
            InboundWebhookEvent event,
            Exception exception
    ) {
        event.setStatus(InboundWebhookEventStatus.FAILED);
        event.setFailureReason(exception.getMessage());
        event.setProcessedAt(Instant.now());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}