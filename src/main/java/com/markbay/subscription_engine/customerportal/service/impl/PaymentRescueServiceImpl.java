package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.config.CustomerPortalProperties;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalOverviewResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalTokenPair;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueCheckoutResponse;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueLinkResponse;
import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionPurpose;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.customerportal.enums.PaymentRescueCheckoutStatus;
import com.markbay.subscription_engine.customerportal.repository.CustomerPortalSessionRepository;
import com.markbay.subscription_engine.customerportal.repository.PaymentRescueCheckoutSessionRepository;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalTokenService;
import com.markbay.subscription_engine.customerportal.service.PaymentRescueService;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.dunning.repository.DunningCaseRepository;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
import com.markbay.subscription_engine.invoice.repository.InvoiceRepository;
import com.markbay.subscription_engine.nomba.dto.request.NombaCheckoutOrder;
import com.markbay.subscription_engine.nomba.dto.request.NombaCreateCheckoutOrderRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaCheckoutOrderResult;
import com.markbay.subscription_engine.nomba.gateway.NombaCheckoutGateway;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRescueServiceImpl implements PaymentRescueService {

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final CustomerPortalProperties customerPortalProperties;
    private final CustomerPortalTokenService tokenService;
    private final CustomerPortalSessionRepository portalSessionRepository;
    private final PaymentRescueCheckoutSessionRepository rescueCheckoutSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final DunningCaseRepository dunningCaseRepository;
    private final NombaCheckoutGateway nombaCheckoutGateway;

    @Override
    @Transactional
    public PaymentRescueLinkResponse createPaymentRescueLinkForInvoice(UUID invoiceId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Invoice invoice = invoiceRepository.findByIdAndTenant_Id(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        DunningCase dunningCase = dunningCaseRepository.findByInvoice_Id(invoice.getId())
                .orElse(null);

        return createPaymentRescueLink(
                invoice.getSubscription(),
                invoice,
                dunningCase
        );
    }

    @Override
    @Transactional
    public PaymentRescueLinkResponse createPaymentRescueLink(
            Subscription subscription,
            Invoice invoice,
            DunningCase dunningCase
    ) {
        validateInvoiceCanBeRescued(invoice);
        validateSubscriptionCanBeRescued(subscription);

        CustomerPortalTokenPair tokenPair = tokenService.generateToken();

        Instant expiresAt = Instant.now().plus(
                customerPortalProperties.getRescueLinkExpiryHours(),
                ChronoUnit.HOURS
        );

        CustomerPortalSession session = CustomerPortalSession.builder()
                .tenant(subscription.getTenant())
                .customer(subscription.getCustomer())
                .subscription(subscription)
                .invoice(invoice)
                .dunningCase(dunningCase)
                .tokenHash(tokenPair.tokenHash())
                .purpose(CustomerPortalSessionPurpose.PAYMENT_RESCUE)
                .status(CustomerPortalSessionStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        CustomerPortalSession savedSession =
                portalSessionRepository.save(session);

        String rescueUrl = buildPortalUrl(tokenPair.rawToken());

        log.info(
                "Payment rescue link created. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}, portalSessionId={}, expiresAt={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                invoice.getId(),
                savedSession.getId(),
                expiresAt
        );

        return new PaymentRescueLinkResponse(
                savedSession.getId(),
                invoice.getId(),
                subscription.getId(),
                rescueUrl,
                expiresAt
        );
    }

    @Override
    @Transactional
    public CustomerPortalOverviewResponse getPortalOverview(String rawToken) {
        CustomerPortalSession session = requireActiveSession(rawToken);

        return CustomerPortalOverviewResponse.from(session);
    }

    @Override
    @Transactional
    public PaymentRescueCheckoutResponse createPaymentRescueCheckout(String rawToken) {
        CustomerPortalSession session = requireActiveSession(rawToken);

        if (session.getPurpose() != CustomerPortalSessionPurpose.PAYMENT_RESCUE) {
            throw new BadRequestException("This portal session cannot be used for payment rescue");
        }

        Invoice invoice = session.getInvoice();
        Subscription subscription = session.getSubscription();

        validateInvoiceCanBeRescued(invoice);
        validateSubscriptionCanBeRescued(subscription);

        String orderReference = generateOrderReference();

        PaymentRescueCheckoutSession rescueCheckoutSession =
                PaymentRescueCheckoutSession.builder()
                        .tenant(session.getTenant())
                        .customer(session.getCustomer())
                        .subscription(subscription)
                        .invoice(invoice)
                        .dunningCase(session.getDunningCase())
                        .portalSession(session)
                        .amount(invoice.getAmountDue())
                        .currency(invoice.getCurrency())
                        .status(PaymentRescueCheckoutStatus.PENDING)
                        .orderReference(orderReference)
                        .expiresAt(session.getExpiresAt())
                        .build();

        PaymentRescueCheckoutSession savedSession =
                rescueCheckoutSessionRepository.save(rescueCheckoutSession);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("purpose", "PAYMENT_RESCUE");
        metadata.put("tenantId", session.getTenant().getId().toString());
        metadata.put("customerId", session.getCustomer().getId().toString());
        metadata.put("subscriptionId", subscription.getId().toString());
        metadata.put("invoiceId", invoice.getId().toString());
        metadata.put("portalSessionId", session.getId().toString());
        metadata.put("paymentRescueCheckoutSessionId", savedSession.getId().toString());
        metadata.put("orderReference", orderReference);

        NombaCreateCheckoutOrderRequest request =
                new NombaCreateCheckoutOrderRequest(
                        new NombaCheckoutOrder(
                                invoice.getAmountDue()
                                        .setScale(2, RoundingMode.HALF_UP)
                                        .toPlainString(),
                                invoice.getCurrency(),
                                orderReference,
                                null,
                                session.getCustomer().getEmail(),
                                session.getCustomer().getId().toString(),
                                null,
                                metadata,
                                List.of("Card")
                        ),
                        true,
                        metadata
                );

        NombaCheckoutOrderResult result =
                nombaCheckoutGateway.createCheckoutOrder(request);

        savedSession.setProviderOrderReference(result.orderReference());
        savedSession.setProviderCheckoutUrl(result.checkoutLink());
        savedSession.setProviderRawResponse(result.rawResponse());
        savedSession.setStatus(PaymentRescueCheckoutStatus.PAYMENT_PENDING);

        log.info(
                "Payment rescue checkout created. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}, orderReference={}",
                session.getTenant().getId(),
                session.getCustomer().getId(),
                subscription.getId(),
                invoice.getId(),
                orderReference
        );

        return PaymentRescueCheckoutResponse.from(savedSession);
    }

    private CustomerPortalSession requireActiveSession(String rawToken) {
        if (!hasText(rawToken)) {
            throw new BadRequestException("Portal token is required");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        CustomerPortalSession session = portalSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Customer portal session not found"));

        if (session.getStatus() != CustomerPortalSessionStatus.ACTIVE) {
            throw new BadRequestException("Customer portal session is not active");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(CustomerPortalSessionStatus.EXPIRED);
            throw new BadRequestException("Customer portal session has expired");
        }

        return session;
    }

    private void validateInvoiceCanBeRescued(Invoice invoice) {
        if (invoice == null) {
            throw new BadRequestException("Invoice is required");
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("Invoice has already been paid");
        }

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new BadRequestException("Void invoice cannot be paid");
        }

        if (invoice.getStatus() != InvoiceStatus.OPEN
                && invoice.getStatus() != InvoiceStatus.UNCOLLECTIBLE) {
            throw new BadRequestException("Invoice is not payable");
        }
    }

    private void validateSubscriptionCanBeRescued(Subscription subscription) {
        if (subscription == null) {
            throw new BadRequestException("Subscription is required");
        }

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return;
        }

        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            return;
        }

        throw new BadRequestException("Subscription cannot be recovered from current status");
    }

    private String buildPortalUrl(String rawToken) {
        String baseUrl = customerPortalProperties.getPublicBaseUrl();

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/api/v1/customer-portal/sessions/" + rawToken;
    }

    private String generateOrderReference() {
        return "rescue_" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}