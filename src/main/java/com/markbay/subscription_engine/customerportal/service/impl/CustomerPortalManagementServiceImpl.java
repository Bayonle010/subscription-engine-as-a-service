package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.config.CustomerPortalProperties;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalActionResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalInvoiceResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalManagementLinkResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalSubscriptionResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalTokenPair;
import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionPurpose;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.customerportal.repository.CustomerPortalSessionRepository;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalManagementService;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalTokenService;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.repository.InvoiceRepository;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPortalManagementServiceImpl
        implements CustomerPortalManagementService {

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final CustomerPortalProperties customerPortalProperties;
    private final CustomerPortalTokenService tokenService;
    private final CustomerPortalSessionRepository portalSessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public CustomerPortalManagementLinkResponse createManagementLink(UUID subscriptionId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Subscription subscription = subscriptionRepository.findByIdAndTenant_Id(
                        subscriptionId,
                        tenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        CustomerPortalTokenPair tokenPair = tokenService.generateToken();

        Instant expiresAt = Instant.now().plus(
                customerPortalProperties.getManagementLinkExpiryHours(),
                ChronoUnit.HOURS
        );

        CustomerPortalSession session = CustomerPortalSession.builder()
                .tenant(subscription.getTenant())
                .customer(subscription.getCustomer())
                .subscription(subscription)
                .invoice(null)
                .dunningCase(null)
                .tokenHash(tokenPair.tokenHash())
                .purpose(CustomerPortalSessionPurpose.MANAGE_SUBSCRIPTION)
                .status(CustomerPortalSessionStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        CustomerPortalSession savedSession =
                portalSessionRepository.save(session);

        String portalUrl = buildPortalUrl(tokenPair.rawToken());

        log.info(
                "Customer portal management link created. tenantId={}, customerId={}, subscriptionId={}, portalSessionId={}, expiresAt={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                savedSession.getId(),
                expiresAt
        );

        return new CustomerPortalManagementLinkResponse(
                savedSession.getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                portalUrl,
                expiresAt
        );
    }

    @Override
    @Transactional
    public CustomerPortalSubscriptionResponse getSubscription(String rawToken) {
        CustomerPortalSession session = requireManagementSession(rawToken, false);

        return CustomerPortalSubscriptionResponse.from(session);
    }

    @Override
    @Transactional
    public List<CustomerPortalInvoiceResponse> listInvoices(String rawToken) {
        CustomerPortalSession session = requireManagementSession(rawToken, false);

        return invoiceRepository.findAllByTenant_IdAndSubscription_IdOrderByCreatedAtDesc(
                        session.getTenant().getId(),
                        session.getSubscription().getId()
                )
                .stream()
                .map(CustomerPortalInvoiceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CustomerPortalActionResponse cancelAtPeriodEnd(String rawToken) {
        CustomerPortalSession session = requireManagementSession(rawToken, true);

        Subscription subscription = session.getSubscription();

        if (!isActiveOrTrialing(subscription)) {
            throw new BadRequestException(
                    "Only active or trialing subscriptions can be cancelled at period end"
            );
        }

        if (subscription.isCancelAtPeriodEnd()) {
            return toActionResponse(
                    session,
                    "Subscription is already scheduled for cancellation at period end"
            );
        }

        subscription.setCancelAtPeriodEnd(true);

        recordSubscriptionUpdatedEvent(
                subscription,
                "CANCEL_AT_PERIOD_END",
                "Subscription scheduled for cancellation at period end"
        );

        log.info(
                "Subscription scheduled for cancellation at period end. subscriptionId={}, currentPeriodEnd={}",
                subscription.getId(),
                subscription.getCurrentPeriodEnd()
        );

        return toActionResponse(
                session,
                "Subscription will be cancelled at the end of the current billing period"
        );
    }

    @Override
    @Transactional
    public CustomerPortalActionResponse cancelNow(String rawToken) {
        CustomerPortalSession session = requireManagementSession(rawToken, true);

        Subscription subscription = session.getSubscription();

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            return toActionResponse(
                    session,
                    "Subscription is already cancelled"
            );
        }

        Instant now = Instant.now();

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(now);

        recordSubscriptionCancelledEvent(
                subscription,
                "CUSTOMER_CANCELLED_IMMEDIATELY",
                "Subscription cancelled immediately by customer"
        );

        log.info(
                "Subscription cancelled immediately from customer portal. subscriptionId={}",
                subscription.getId()
        );

        return toActionResponse(
                session,
                "Subscription cancelled successfully"
        );
    }

    @Override
    @Transactional
    public CustomerPortalActionResponse resumeCancellation(String rawToken) {
        CustomerPortalSession session = requireManagementSession(rawToken, true);

        Subscription subscription = session.getSubscription();

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cancelled subscription cannot be resumed from this endpoint"
            );
        }

        if (!subscription.isCancelAtPeriodEnd()) {
            return toActionResponse(
                    session,
                    "Subscription is not scheduled for cancellation"
            );
        }

        subscription.setCancelAtPeriodEnd(false);

        recordSubscriptionUpdatedEvent(
                subscription,
                "RESUME_CANCELLATION",
                "Scheduled cancellation removed by customer"
        );

        log.info(
                "Subscription scheduled cancellation resumed. subscriptionId={}",
                subscription.getId()
        );

        return toActionResponse(
                session,
                "Subscription cancellation has been resumed"
        );
    }

    private CustomerPortalSession requireManagementSession(
            String rawToken,
            boolean forUpdate
    ) {
        if (!hasText(rawToken)) {
            throw new BadRequestException("Portal token is required");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        CustomerPortalSession session = forUpdate
                ? portalSessionRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Customer portal session not found"))
                : portalSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Customer portal session not found"));

        if (session.getStatus() != CustomerPortalSessionStatus.ACTIVE) {
            throw new BadRequestException("Customer portal session is not active");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(CustomerPortalSessionStatus.EXPIRED);
            throw new BadRequestException("Customer portal session has expired");
        }

        if (session.getPurpose() != CustomerPortalSessionPurpose.MANAGE_SUBSCRIPTION) {
            throw new BadRequestException(
                    "This customer portal session cannot be used to manage subscription"
            );
        }

        if (session.getSubscription() == null) {
            throw new BadRequestException("Customer portal session has no subscription");
        }

        return session;
    }

    private CustomerPortalActionResponse toActionResponse(
            CustomerPortalSession session,
            String message
    ) {
        Subscription subscription = session.getSubscription();

        return new CustomerPortalActionResponse(
                session.getId(),
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCancelledAt(),
                message
        );
    }

    private boolean isActiveOrTrialing(Subscription subscription) {
        return subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING;
    }

    private void recordSubscriptionUpdatedEvent(
            Subscription subscription,
            String action,
            String message
    ) {
        Map<String, String> payload = buildSubscriptionPayload(
                subscription,
                action,
                message
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_UPDATED)
                        .eventReference(buildEventReference(
                                "subscription.updated",
                                subscription,
                                action
                        ))
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private void recordSubscriptionCancelledEvent(
            Subscription subscription,
            String action,
            String message
    ) {
        Map<String, String> payload = buildSubscriptionPayload(
                subscription,
                action,
                message
        );

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

    private Map<String, String> buildSubscriptionPayload(
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
        payload.put("amount", subscription.getAmount().toPlainString());
        payload.put("currency", subscription.getCurrency());
        payload.put("cancelAtPeriodEnd", Boolean.toString(subscription.isCancelAtPeriodEnd()));
        payload.put("currentPeriodStart", safeInstant(subscription.getCurrentPeriodStart()));
        payload.put("currentPeriodEnd", safeInstant(subscription.getCurrentPeriodEnd()));
        payload.put("cancelledAt", safeInstant(subscription.getCancelledAt()));
        payload.put("action", action);
        payload.put("message", message);

        return payload;
    }

    private String buildEventReference(
            String prefix,
            Subscription subscription,
            String action
    ) {
        return prefix
                + ":"
                + subscription.getId()
                + ":"
                + action.toLowerCase(Locale.ROOT)
                + ":"
                + Instant.now().toEpochMilli();
    }

    private String buildPortalUrl(String rawToken) {
        String baseUrl = customerPortalProperties.getPublicBaseUrl();

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/api/v1/customer-portal/sessions/" + rawToken;
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}