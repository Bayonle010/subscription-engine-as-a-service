package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.dto.PaymentMethodUpdateRequestResponse;
import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionPurpose;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.customerportal.repository.CustomerPortalSessionRepository;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalTokenService;
import com.markbay.subscription_engine.customerportal.service.PaymentMethodUpdateRequestService;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodUpdateRequestServiceImpl
        implements PaymentMethodUpdateRequestService {

    private final CustomerPortalTokenService tokenService;
    private final CustomerPortalSessionRepository portalSessionRepository;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public PaymentMethodUpdateRequestResponse requestPaymentMethodUpdate(
            String rawToken
    ) {
        CustomerPortalSession portalSession = requireManagementSession(rawToken);

        Subscription subscription = portalSession.getSubscription();

        validateSubscriptionCanRequestUpdate(subscription);

        if (subscription.isPaymentMethodUpdateRequested()) {
            return PaymentMethodUpdateRequestResponse.from(
                    subscription,
                    "Payment method update has already been requested"
            );
        }

        Instant now = Instant.now();

        subscription.setPaymentMethodUpdateRequested(true);
        subscription.setPaymentMethodUpdateRequestedAt(now);
        subscription.setPaymentMethodUpdateFulfilledAt(null);

        recordSubscriptionUpdatedEvent(
                subscription,
                "PAYMENT_METHOD_UPDATE_REQUESTED",
                "Customer requested to update payment method on next renewal"
        );

        log.info(
                "Payment method update requested. tenantId={}, customerId={}, subscriptionId={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId()
        );

        return PaymentMethodUpdateRequestResponse.from(
                subscription,
                "Payment method update requested successfully. The customer will be asked to use a new card at the next renewal."
        );
    }

    @Override
    @Transactional
    public PaymentMethodUpdateRequestResponse cancelPaymentMethodUpdateRequest(
            String rawToken
    ) {
        CustomerPortalSession portalSession = requireManagementSession(rawToken);

        Subscription subscription = portalSession.getSubscription();

        if (!subscription.isPaymentMethodUpdateRequested()) {
            return PaymentMethodUpdateRequestResponse.from(
                    subscription,
                    "There is no pending payment method update request"
            );
        }

        subscription.setPaymentMethodUpdateRequested(false);

        recordSubscriptionUpdatedEvent(
                subscription,
                "PAYMENT_METHOD_UPDATE_REQUEST_CANCELLED",
                "Customer cancelled payment method update request"
        );

        log.info(
                "Payment method update request cancelled. tenantId={}, customerId={}, subscriptionId={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId()
        );

        return PaymentMethodUpdateRequestResponse.from(
                subscription,
                "Payment method update request cancelled successfully"
        );
    }

    private CustomerPortalSession requireManagementSession(String rawToken) {
        if (!hasText(rawToken)) {
            throw new BadRequestException("Portal token is required");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        CustomerPortalSession session = portalSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer portal session not found"
                ));

        if (session.getStatus() != CustomerPortalSessionStatus.ACTIVE) {
            throw new BadRequestException("Customer portal session is not active");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(CustomerPortalSessionStatus.EXPIRED);
            throw new BadRequestException("Customer portal session has expired");
        }

        if (session.getPurpose() != CustomerPortalSessionPurpose.MANAGE_SUBSCRIPTION) {
            throw new BadRequestException(
                    "This customer portal session cannot be used to update payment method"
            );
        }

        if (session.getSubscription() == null) {
            throw new BadRequestException("Customer portal session has no subscription");
        }

        return session;
    }

    private void validateSubscriptionCanRequestUpdate(Subscription subscription) {
        if (subscription == null) {
            throw new BadRequestException("Subscription is required");
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED
                || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new BadRequestException(
                    "Payment method cannot be updated for cancelled or expired subscription"
            );
        }
    }

    private void recordSubscriptionUpdatedEvent(
            Subscription subscription,
            String action,
            String message
    ) {
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
        payload.put("paymentMethodUpdateRequested", Boolean.toString(
                subscription.isPaymentMethodUpdateRequested()
        ));
        payload.put("paymentMethodUpdateRequestedAt", safeInstant(
                subscription.getPaymentMethodUpdateRequestedAt()
        ));
        payload.put("currentPeriodEnd", safeInstant(subscription.getCurrentPeriodEnd()));
        payload.put("action", action);
        payload.put("message", message);

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_UPDATED)
                        .eventReference("subscription.updated:" + action + ":" + subscription.getId())
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private String buildCustomerName(
            String firstName,
            String lastName
    ) {
        String fullName = (safe(firstName) + " " + safe(lastName)).trim();

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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}