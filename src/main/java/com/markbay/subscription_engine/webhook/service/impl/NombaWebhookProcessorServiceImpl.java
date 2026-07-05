package com.markbay.subscription_engine.webhook.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.repository.PaymentRescueCheckoutSessionRepository;
import com.markbay.subscription_engine.customerportal.service.PaymentRescueCompletionService;
import com.markbay.subscription_engine.merchantwithdrawal.dto.NombaPayoutWebhookData;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalVerificationService;
import com.markbay.subscription_engine.merchantwithdrawal.support.NombaPayoutWebhookPayloadExtractor;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.nomba.gateway.NombaTransactionGateway;
import com.markbay.subscription_engine.nomba.support.NombaWebhookPayloadExtractor;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutCompletionService;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutService;
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
    private final PaymentRescueCompletionService paymentRescueCompletionService;
    private final PaymentRescueCheckoutSessionRepository paymentRescueCheckoutSessionRepository;
    private final com.markbay.subscription_engine.renewalcheckout.repository.RenewalCheckoutSessionRepository renewalCheckoutSessionRepository;
    private final RenewalCheckoutCompletionService renewalCheckoutCompletionService;
    private final RenewalCheckoutService renewalCheckoutService;
    private final MerchantWithdrawalVerificationService merchantWithdrawalVerificationService;
    private final NombaPayoutWebhookPayloadExtractor payoutWebhookPayloadExtractor;


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

                case PAYOUT_SUCCESS, PAYOUT_FAILED, PAYOUT_REFUND -> {
                    processPayoutWebhook(event);
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
            log.warn(
                    "Payment success webhook ignored because order reference is missing. eventId={}",
                    event.getId()
            );

            return;
        }

        var subscriptionCheckoutSession =
                checkoutSessionRepository.findByOrderReference(orderReference);

        if (subscriptionCheckoutSession.isPresent()) {
            SubscriptionCheckoutSession checkoutSession =
                    subscriptionCheckoutSession.get();

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

            return;
        }

        var paymentRescueCheckoutSession =
                paymentRescueCheckoutSessionRepository.findByOrderReference(orderReference);

        if (paymentRescueCheckoutSession.isPresent()) {
            NombaVerifiedTransactionResult verifiedTransaction =
                    nombaTransactionGateway.verifyByOrderReference(orderReference);

            paymentRescueCompletionService.completeSuccessfulRescuePayment(
                    orderReference,
                    verifiedTransaction,
                    paymentData
            );

            return;
        }


        var renewalCheckoutSession =
                renewalCheckoutSessionRepository.findByOrderReference(orderReference);

        if (renewalCheckoutSession.isPresent()) {
            NombaVerifiedTransactionResult verifiedTransaction =
                    nombaTransactionGateway.verifyByOrderReference(orderReference);

            renewalCheckoutCompletionService.completeSuccessfulRenewalCheckout(
                    orderReference,
                    verifiedTransaction,
                    paymentData
            );

            return;
        }

        log.warn(
                "Payment success webhook ignored because order reference was not found. eventId={}, orderReference={}",
                event.getId(),
                orderReference
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

        var subscriptionCheckoutSession =
                checkoutSessionRepository.findByOrderReference(orderReference);

        if (subscriptionCheckoutSession.isPresent()) {
            SubscriptionCheckoutSession checkoutSession =
                    subscriptionCheckoutSession.get();

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

            return;
        }

        var paymentRescueCheckoutSession =
                paymentRescueCheckoutSessionRepository.findByOrderReference(orderReference);

        if (paymentRescueCheckoutSession.isPresent()) {
            paymentRescueCompletionService.markRescuePaymentFailed(
                    orderReference,
                    "Payment rescue checkout failed"
            );

            log.info(
                    "Payment rescue checkout marked as failed. orderReference={}",
                    orderReference
            );

            return;
        }

        log.warn(
                "Payment failed webhook ignored because order reference was not found. eventId={}, orderReference={}",
                event.getId(),
                orderReference
        );

        var renewalCheckoutSession =
                renewalCheckoutSessionRepository.findByOrderReference(orderReference);

        if (renewalCheckoutSession.isPresent()) {
            renewalCheckoutService.markRenewalCheckoutFailed(
                    orderReference,
                    "Renewal checkout failed"
            );

            log.info(
                    "Renewal checkout marked as failed. orderReference={}",
                    orderReference
            );

            return;
        }
    }

    private void processPayoutWebhook(InboundWebhookEvent event) {
        JsonNode payload = payloadExtractor.readTree(event.getPayload());

        NombaPayoutWebhookData payoutWebhookData =
                payoutWebhookPayloadExtractor.extract(
                        event.getEventType(),
                        payload
                );

        if (!hasText(payoutWebhookData.merchantTxRef())
                && !hasText(payoutWebhookData.transferId())) {
            log.warn(
                    "Payout webhook ignored because merchantTxRef and transferId are missing. eventId={}, eventType={}",
                    event.getId(),
                    event.getEventType()
            );

            return;
        }

        merchantWithdrawalVerificationService.handlePayoutWebhook(
                payoutWebhookData
        );

        log.info(
                "Payout webhook processed. eventId={}, eventType={}, merchantTxRef={}, transferId={}, providerStatus={}",
                event.getId(),
                event.getEventType(),
                payoutWebhookData.merchantTxRef(),
                payoutWebhookData.transferId(),
                payoutWebhookData.providerStatus()
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