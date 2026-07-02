package com.markbay.subscription_engine.reconciliation.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;
import com.markbay.subscription_engine.customerportal.enums.PaymentRescueCheckoutStatus;
import com.markbay.subscription_engine.customerportal.repository.PaymentRescueCheckoutSessionRepository;
import com.markbay.subscription_engine.customerportal.service.PaymentRescueCompletionService;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.nomba.gateway.NombaTransactionGateway;
import com.markbay.subscription_engine.reconciliation.config.PaymentReconciliationProperties;
import com.markbay.subscription_engine.reconciliation.service.PaymentReconciliationService;
import com.markbay.subscription_engine.reconciliation.support.ReconciliationPaymentDataFactory;
import com.markbay.subscription_engine.renewalcheckout.entity.RenewalCheckoutSession;
import com.markbay.subscription_engine.renewalcheckout.enums.RenewalCheckoutStatus;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutCompletionService;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutService;
import com.markbay.subscription_engine.subscription.service.SubscriptionActivationService;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
import com.markbay.subscription_engine.subscriptioncheckout.repository.SubscriptionCheckoutSessionRepository;
import com.markbay.subscription_engine.webhook.enums.InboundWebhookEventStatus;
import com.markbay.subscription_engine.webhook.repository.InboundWebhookEventRepository;
import com.markbay.subscription_engine.webhook.service.NombaWebhookProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl
        implements PaymentReconciliationService {

    private final PaymentReconciliationProperties reconciliationProperties;

    private final SubscriptionCheckoutSessionRepository checkoutSessionRepository;
    private final PaymentRescueCheckoutSessionRepository rescueCheckoutSessionRepository;
    private final InboundWebhookEventRepository inboundWebhookEventRepository;

    private final NombaTransactionGateway nombaTransactionGateway;
    private final SubscriptionActivationService subscriptionActivationService;
    private final PaymentRescueCompletionService paymentRescueCompletionService;
    private final NombaWebhookProcessorService nombaWebhookProcessorService;
    private final ReconciliationPaymentDataFactory paymentDataFactory;
    private final com.markbay.subscription_engine.renewalcheckout.repository.RenewalCheckoutSessionRepository renewalCheckoutSessionRepository;
    private final RenewalCheckoutCompletionService renewalCheckoutCompletionService;
    private final RenewalCheckoutService renewalCheckoutService;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueSubscriptionCheckoutSessionIds(int batchSize) {
        Instant createdBefore = Instant.now().minus(
                reconciliationProperties.getPendingMinAgeMinutes(),
                ChronoUnit.MINUTES
        );

        return checkoutSessionRepository.findPendingSessionIdsForReconciliation(
                CheckoutSessionStatus.PAYMENT_PENDING,
                createdBefore,
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDuePaymentRescueCheckoutSessionIds(int batchSize) {
        Instant createdBefore = Instant.now().minus(
                reconciliationProperties.getPendingMinAgeMinutes(),
                ChronoUnit.MINUTES
        );

        return rescueCheckoutSessionRepository.findPendingSessionIdsForReconciliation(
                PaymentRescueCheckoutStatus.PAYMENT_PENDING,
                createdBefore,
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findFailedWebhookEventIds(int batchSize) {
        if (!reconciliationProperties.isRetryFailedWebhooks()) {
            return List.of();
        }

        Instant updatedBefore = Instant.now().minus(
                reconciliationProperties.getFailedWebhookRetryAgeMinutes(),
                ChronoUnit.MINUTES
        );

        return inboundWebhookEventRepository.findFailedEventIdsForRetry(
                InboundWebhookEventStatus.FAILED,
                updatedBefore,
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional
    public void reconcileSubscriptionCheckoutSession(UUID sessionId) {
        SubscriptionCheckoutSession checkoutSession =
                checkoutSessionRepository.findByIdForReconciliation(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Subscription checkout session not found"
                        ));

        if (checkoutSession.getStatus() == CheckoutSessionStatus.COMPLETED) {
            log.info(
                    "Subscription checkout already completed. checkoutSessionId={}",
                    checkoutSession.getId()
            );
            return;
        }

        if (checkoutSession.getStatus() != CheckoutSessionStatus.PAYMENT_PENDING) {
            log.info(
                    "Skipping subscription checkout reconciliation because status is not PAYMENT_PENDING. checkoutSessionId={}, status={}",
                    checkoutSession.getId(),
                    checkoutSession.getStatus()
            );
            return;
        }

        if (shouldExpire(
                checkoutSession.getCreatedAt(),
                checkoutSession.getExpiresAt()
        )) {
            checkoutSession.setStatus(CheckoutSessionStatus.EXPIRED);

            log.info(
                    "Subscription checkout session expired during reconciliation. checkoutSessionId={}, orderReference={}",
                    checkoutSession.getId(),
                    checkoutSession.getOrderReference()
            );

            return;
        }

        String orderReference = checkoutSession.getOrderReference();

        NombaVerifiedTransactionResult verifiedTransaction =
                nombaTransactionGateway.verifyByOrderReference(orderReference);

        if (verifiedTransaction.success()) {
            NombaWebhookPaymentData paymentData =
                    paymentDataFactory.fromVerifiedTransaction(
                            orderReference,
                            verifiedTransaction
                    );

            if (!hasText(paymentData.tokenKey())) {
                log.warn(
                        "Subscription checkout payment was verified successfully, but tokenKey was not found in verification response. checkoutSessionId={}, orderReference={}",
                        checkoutSession.getId(),
                        orderReference
                );

                return;
            }

            subscriptionActivationService.activateFromSuccessfulCheckout(
                    checkoutSession,
                    verifiedTransaction,
                    paymentData
            );

            log.info(
                    "Subscription checkout reconciled successfully. checkoutSessionId={}, orderReference={}",
                    checkoutSession.getId(),
                    orderReference
            );

            return;
        }

        if (isTerminalFailure(verifiedTransaction.status())) {
            checkoutSession.setStatus(CheckoutSessionStatus.FAILED);
            checkoutSession.setFailedAt(Instant.now());

            log.info(
                    "Subscription checkout marked failed during reconciliation. checkoutSessionId={}, orderReference={}, providerStatus={}",
                    checkoutSession.getId(),
                    orderReference,
                    verifiedTransaction.status()
            );

            return;
        }

        log.info(
                "Subscription checkout still pending after reconciliation. checkoutSessionId={}, orderReference={}, providerStatus={}",
                checkoutSession.getId(),
                orderReference,
                verifiedTransaction.status()
        );
    }

    @Override
    @Transactional
    public void reconcilePaymentRescueCheckoutSession(UUID sessionId) {
        PaymentRescueCheckoutSession rescueSession =
                rescueCheckoutSessionRepository.findByIdForReconciliation(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment rescue checkout session not found"
                        ));

        if (rescueSession.getStatus() == PaymentRescueCheckoutStatus.COMPLETED) {
            log.info(
                    "Payment rescue checkout already completed. rescueSessionId={}",
                    rescueSession.getId()
            );
            return;
        }

        if (rescueSession.getStatus() != PaymentRescueCheckoutStatus.PAYMENT_PENDING) {
            log.info(
                    "Skipping payment rescue reconciliation because status is not PAYMENT_PENDING. rescueSessionId={}, status={}",
                    rescueSession.getId(),
                    rescueSession.getStatus()
            );
            return;
        }

        if (shouldExpire(
                rescueSession.getCreatedAt(),
                rescueSession.getExpiresAt()
        )) {
            rescueSession.setStatus(PaymentRescueCheckoutStatus.EXPIRED);

            log.info(
                    "Payment rescue checkout expired during reconciliation. rescueSessionId={}, orderReference={}",
                    rescueSession.getId(),
                    rescueSession.getOrderReference()
            );

            return;
        }

        String orderReference = rescueSession.getOrderReference();

        NombaVerifiedTransactionResult verifiedTransaction =
                nombaTransactionGateway.verifyByOrderReference(orderReference);

        if (verifiedTransaction.success()) {
            NombaWebhookPaymentData paymentData =
                    paymentDataFactory.fromVerifiedTransaction(
                            orderReference,
                            verifiedTransaction
                    );

            paymentRescueCompletionService.completeSuccessfulRescuePayment(
                    orderReference,
                    verifiedTransaction,
                    paymentData
            );

            log.info(
                    "Payment rescue checkout reconciled successfully. rescueSessionId={}, orderReference={}",
                    rescueSession.getId(),
                    orderReference
            );

            return;
        }

        if (isTerminalFailure(verifiedTransaction.status())) {
            paymentRescueCompletionService.markRescuePaymentFailed(
                    orderReference,
                    "Payment rescue checkout failed during reconciliation"
            );

            log.info(
                    "Payment rescue checkout marked failed during reconciliation. rescueSessionId={}, orderReference={}, providerStatus={}",
                    rescueSession.getId(),
                    orderReference,
                    verifiedTransaction.status()
            );

            return;
        }

        log.info(
                "Payment rescue checkout still pending after reconciliation. rescueSessionId={}, orderReference={}, providerStatus={}",
                rescueSession.getId(),
                orderReference,
                verifiedTransaction.status()
        );
    }

    @Override
    public void retryFailedWebhookEvent(UUID eventId) {
        log.info("Retrying failed inbound webhook event. eventId={}", eventId);

        nombaWebhookProcessorService.processWebhookEvent(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueRenewalCheckoutSessionIds(int batchSize) {
        Instant createdBefore = Instant.now().minus(
                reconciliationProperties.getPendingMinAgeMinutes(),
                ChronoUnit.MINUTES
        );

        return renewalCheckoutSessionRepository.findPendingSessionIdsForReconciliation(
                RenewalCheckoutStatus.PAYMENT_PENDING,
                createdBefore,
                PageRequest.of(0, batchSize)
        );
    }


    @Override
    @Transactional
    public void reconcileRenewalCheckoutSession(UUID sessionId) {
        RenewalCheckoutSession session =
                renewalCheckoutSessionRepository.findByIdForUpdate(sessionId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Renewal checkout session not found"
                        ));

        if (session.getStatus() == RenewalCheckoutStatus.COMPLETED) {
            log.info(
                    "Renewal checkout already completed. renewalCheckoutSessionId={}",
                    session.getId()
            );

            return;
        }

        if (session.getStatus() != RenewalCheckoutStatus.PAYMENT_PENDING) {
            log.info(
                    "Skipping renewal checkout reconciliation because status is not PAYMENT_PENDING. renewalCheckoutSessionId={}, status={}",
                    session.getId(),
                    session.getStatus()
            );

            return;
        }

        if (shouldExpire(session.getCreatedAt(), session.getExpiresAt())) {
            session.setStatus(RenewalCheckoutStatus.EXPIRED);

            log.info(
                    "Renewal checkout expired during reconciliation. renewalCheckoutSessionId={}, orderReference={}",
                    session.getId(),
                    session.getOrderReference()
            );

            return;
        }

        String orderReference = session.getOrderReference();

        NombaVerifiedTransactionResult verifiedTransaction =
                nombaTransactionGateway.verifyByOrderReference(orderReference);

        if (verifiedTransaction.success()) {
            NombaWebhookPaymentData paymentData =
                    paymentDataFactory.fromVerifiedTransaction(
                            orderReference,
                            verifiedTransaction
                    );

            renewalCheckoutCompletionService.completeSuccessfulRenewalCheckout(
                    orderReference,
                    verifiedTransaction,
                    paymentData
            );

            log.info(
                    "Renewal checkout reconciled successfully. renewalCheckoutSessionId={}, orderReference={}",
                    session.getId(),
                    orderReference
            );

            return;
        }

        if (isTerminalFailure(verifiedTransaction.status())) {
            renewalCheckoutService.markRenewalCheckoutFailed(
                    orderReference,
                    "Renewal checkout failed during reconciliation"
            );

            log.info(
                    "Renewal checkout marked failed during reconciliation. renewalCheckoutSessionId={}, orderReference={}, providerStatus={}",
                    session.getId(),
                    orderReference,
                    verifiedTransaction.status()
            );

            return;
        }

        log.info(
                "Renewal checkout still pending after reconciliation. renewalCheckoutSessionId={}, orderReference={}, providerStatus={}",
                session.getId(),
                orderReference,
                verifiedTransaction.status()
        );
    }

    private boolean shouldExpire(
            Instant createdAt,
            Instant expiresAt
    ) {
        Instant now = Instant.now();

        if (expiresAt != null && expiresAt.isBefore(now)) {
            return true;
        }

        if (createdAt == null) {
            return false;
        }

        Instant expiryCutoff = createdAt.plus(
                reconciliationProperties.getPendingExpireAfterHours(),
                ChronoUnit.HOURS
        );

        return expiryCutoff.isBefore(now);
    }

    private boolean isTerminalFailure(String status) {
        if (!hasText(status)) {
            return false;
        }

        String normalized = status.trim().toUpperCase();

        return normalized.contains("FAILED")
                || normalized.contains("FAILURE")
                || normalized.contains("DECLINED")
                || normalized.contains("CANCELLED")
                || normalized.contains("CANCELED")
                || normalized.contains("EXPIRED");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}