package com.markbay.subscription_engine.subscriptioncheckout.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.financial.service.TenantFinancialAccountService;
import com.markbay.subscription_engine.nomba.dto.request.NombaCheckoutOrder;
import com.markbay.subscription_engine.nomba.dto.request.NombaCreateCheckoutOrderRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaCheckoutOrderResult;
import com.markbay.subscription_engine.nomba.gateway.NombaCheckoutGateway;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import com.markbay.subscription_engine.plan.repository.PlanRepository;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.subscriptioncheckout.dto.CreateSubscriptionCheckoutSessionRequest;
import com.markbay.subscription_engine.subscriptioncheckout.dto.SubscriptionCheckoutSessionResponse;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
import com.markbay.subscription_engine.subscriptioncheckout.repository.SubscriptionCheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
public class SubscriptionCheckoutSessionServiceImpl
        implements com.markbay.subscription_engine.subscriptioncheckout.service.SubscriptionCheckoutSessionService {

    private static final int CHECKOUT_SESSION_EXPIRY_HOURS = 24;

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final TenantFinancialAccountService tenantFinancialAccountService;
    private final PlanRepository planRepository;
    private final SubscriptionCheckoutSessionRepository checkoutSessionRepository;
    private final NombaCheckoutGateway nombaCheckoutGateway;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SubscriptionCheckoutSessionResponse createCheckoutSession(
            CreateSubscriptionCheckoutSessionRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        tenantFinancialAccountService.requireActiveFinancialAccount(tenantId);

        Plan plan = planRepository.findByIdAndTenant_Id(request.planId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        validatePlanIsUsable(plan);
        validatePaymentMethod(request.paymentMethodType());

        String orderReference = generateOrderReference();

        SubscriptionCheckoutSession session = SubscriptionCheckoutSession.builder()
                .tenant(plan.getTenant())
                .plan(plan)
                .amount(plan.getAmount())
                .currency(plan.getCurrency())
                .paymentMethodType(request.paymentMethodType())
                .status(CheckoutSessionStatus.PENDING)
                .customerEmail(normalizeEmail(request.customerEmail()))
                .customerFirstName(trimToNull(request.customerFirstName()))
                .customerLastName(trimToNull(request.customerLastName()))
                .customerPhone(trimToNull(request.customerPhone()))
                .orderReference(orderReference)
                .successUrl(request.successUrl().trim())
                .cancelUrl(trimToNull(request.cancelUrl()))
                .metadataJson(toJson(request.metadata()))
                .expiresAt(Instant.now().plus(CHECKOUT_SESSION_EXPIRY_HOURS, ChronoUnit.HOURS))
                .build();

        SubscriptionCheckoutSession savedSession =
                checkoutSessionRepository.save(session);

        NombaCreateCheckoutOrderRequest nombaRequest =
                buildNombaCheckoutOrderRequest(savedSession, request.metadata());

        NombaCheckoutOrderResult nombaResult =
                nombaCheckoutGateway.createCheckoutOrder(nombaRequest);

        savedSession.setProviderOrderReference(nombaResult.orderReference());
        savedSession.setProviderCheckoutUrl(nombaResult.checkoutLink());
        savedSession.setProviderRawResponse(nombaResult.rawResponse());
        savedSession.setStatus(CheckoutSessionStatus.PAYMENT_PENDING);

        SubscriptionCheckoutSession updatedSession =
                checkoutSessionRepository.save(savedSession);

        log.info(
                "Subscription checkout session created. tenantId={}, sessionId={}, orderReference={}",
                tenantId,
                updatedSession.getId(),
                updatedSession.getOrderReference()
        );

        return SubscriptionCheckoutSessionResponse.from(updatedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionCheckoutSessionResponse getCheckoutSession(UUID sessionId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        SubscriptionCheckoutSession session = checkoutSessionRepository
                .findByIdAndTenant_Id(sessionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription checkout session not found"
                ));

        return SubscriptionCheckoutSessionResponse.from(session);
    }

    private void validatePlanIsUsable(Plan plan) {
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new BadRequestException("Plan is not active");
        }

        if (plan.getProduct() == null) {
            throw new BadRequestException("Plan product is missing");
        }

        if (plan.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("Product is not active");
        }

        if (plan.getAmount() == null || plan.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Plan amount must be greater than zero");
        }

        if (!hasText(plan.getCurrency())) {
            throw new BadRequestException("Plan currency is required");
        }
    }

    private void validatePaymentMethod(PaymentMethodType paymentMethodType) {
        if (paymentMethodType == null) {
            throw new BadRequestException("Payment method type is required");
        }

        if (paymentMethodType != PaymentMethodType.CARD) {
            throw new BadRequestException(
                    "Only card subscription checkout is supported for now"
            );
        }
    }

    private NombaCreateCheckoutOrderRequest buildNombaCheckoutOrderRequest(
            SubscriptionCheckoutSession session,
            Map<String, String> requestMetadata
    ) {
        Map<String, String> metadata = buildOrderMetadata(session, requestMetadata);

        NombaCheckoutOrder order = new NombaCheckoutOrder(
                formatAmount(session.getAmount()),
                session.getCurrency(),
                session.getOrderReference(),
                session.getSuccessUrl(),
                session.getCustomerEmail(),
                session.getId().toString(),
                null,
                metadata,
               null// List.of("Card")
        );

        return new NombaCreateCheckoutOrderRequest(
                order,
                true,
                metadata
        );
    }

    private Map<String, String> buildOrderMetadata(
            SubscriptionCheckoutSession session,
            Map<String, String> requestMetadata
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();

        metadata.put("tenantId", session.getTenant().getId().toString());
        metadata.put("checkoutSessionId", session.getId().toString());
        metadata.put("planId", session.getPlan().getId().toString());
        metadata.put("orderReference", session.getOrderReference());
        metadata.put("purpose", "subscription_initial_checkout");

        if (requestMetadata != null && !requestMetadata.isEmpty()) {
            requestMetadata.forEach((key, value) -> {
                if (hasText(key) && hasText(value)) {
                    metadata.put(key.trim(), value.trim());
                }
            });
        }

        return metadata;
    }

    private String generateOrderReference() {
        return "sub_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String toJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new BadRequestException("Invalid metadata");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}