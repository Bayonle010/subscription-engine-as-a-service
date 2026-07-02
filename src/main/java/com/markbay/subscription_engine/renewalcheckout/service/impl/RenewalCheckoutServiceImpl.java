package com.markbay.subscription_engine.renewalcheckout.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.nomba.dto.request.NombaCheckoutOrder;
import com.markbay.subscription_engine.nomba.dto.request.NombaCreateCheckoutOrderRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaCheckoutOrderResult;
import com.markbay.subscription_engine.nomba.gateway.NombaCheckoutGateway;
import com.markbay.subscription_engine.nomba.support.NombaMoneyFormatter;
import com.markbay.subscription_engine.renewalcheckout.config.RenewalCheckoutProperties;
import com.markbay.subscription_engine.renewalcheckout.entity.RenewalCheckoutSession;
import com.markbay.subscription_engine.renewalcheckout.enums.RenewalCheckoutStatus;
import com.markbay.subscription_engine.renewalcheckout.repository.RenewalCheckoutSessionRepository;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalCheckoutServiceImpl implements RenewalCheckoutService {

    private final RenewalCheckoutProperties renewalCheckoutProperties;
    private final RenewalCheckoutSessionRepository renewalCheckoutSessionRepository;
    private final NombaCheckoutGateway nombaCheckoutGateway;
    private final NombaMoneyFormatter nombaMoneyFormatter;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public RenewalCheckoutSession createCheckoutForPaymentMethodUpdateRenewal(
            Subscription subscription,
            Invoice invoice
    ) {
        if (subscription == null) {
            throw new BadRequestException("Subscription is required");
        }

        if (invoice == null) {
            throw new BadRequestException("Invoice is required");
        }

        return renewalCheckoutSessionRepository.findByInvoice_Id(invoice.getId())
                .orElseGet(() -> createNewRenewalCheckoutSession(subscription, invoice));
    }

    @Override
    @Transactional
    public void markRenewalCheckoutFailed(
            String orderReference,
            String reason
    ) {
        renewalCheckoutSessionRepository.findByOrderReference(orderReference)
                .ifPresent(session -> {
                    if (session.getStatus() == RenewalCheckoutStatus.COMPLETED) {
                        log.info(
                                "Skipping failed renewal checkout update because checkout is already completed. orderReference={}",
                                orderReference
                        );

                        return;
                    }

                    session.setStatus(RenewalCheckoutStatus.FAILED);
                    session.setFailedAt(Instant.now());
                    session.setFailureReason(reason);

                    log.warn(
                            "Renewal checkout failed. orderReference={}, reason={}",
                            orderReference,
                            reason
                    );
                });
    }

    private RenewalCheckoutSession createNewRenewalCheckoutSession(
            Subscription subscription,
            Invoice invoice
    ) {
        String orderReference = generateOrderReference();

        Instant expiresAt = Instant.now().plus(
                renewalCheckoutProperties.getCheckoutExpiryHours(),
                ChronoUnit.HOURS
        );

        RenewalCheckoutSession session =
                RenewalCheckoutSession.builder()
                        .tenant(subscription.getTenant())
                        .customer(subscription.getCustomer())
                        .subscription(subscription)
                        .invoice(invoice)
                        .oldPaymentMethod(subscription.getPaymentMethod())
                        .amount(invoice.getAmountDue())
                        .currency(invoice.getCurrency())
                        .status(RenewalCheckoutStatus.PENDING)
                        .orderReference(orderReference)
                        .expiresAt(expiresAt)
                        .build();

        RenewalCheckoutSession savedSession =
                renewalCheckoutSessionRepository.save(session);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("purpose", "RENEWAL_PAYMENT_METHOD_UPDATE");
        metadata.put("tenantId", subscription.getTenant().getId().toString());
        metadata.put("customerId", subscription.getCustomer().getId().toString());
        metadata.put("subscriptionId", subscription.getId().toString());
        metadata.put("invoiceId", invoice.getId().toString());
        metadata.put("renewalCheckoutSessionId", savedSession.getId().toString());
        metadata.put("orderReference", orderReference);

        NombaCreateCheckoutOrderRequest request =
                new NombaCreateCheckoutOrderRequest(
                        new NombaCheckoutOrder(
                                nombaMoneyFormatter.toCheckoutAmount(invoice.getAmountDue()),
                                invoice.getCurrency(),
                                orderReference,
                                null,
                                subscription.getCustomer().getEmail(),
                                subscription.getCustomer().getId().toString(),
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
        savedSession.setStatus(RenewalCheckoutStatus.PAYMENT_PENDING);

        recordPaymentMethodUpdateRequiredEvent(
                subscription,
                invoice,
                savedSession
        );

        log.info(
                "Renewal checkout created for payment method update. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}, checkoutSessionId={}, orderReference={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                invoice.getId(),
                savedSession.getId(),
                orderReference
        );

        return savedSession;
    }

    private void recordPaymentMethodUpdateRequiredEvent(
            Subscription subscription,
            Invoice invoice,
            RenewalCheckoutSession session
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
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("amount", invoice.getAmountDue().toPlainString());
        payload.put("currency", invoice.getCurrency());
        payload.put("checkoutSessionId", session.getId().toString());
        payload.put("checkoutUrl", safe(session.getProviderCheckoutUrl()));
        payload.put("orderReference", session.getOrderReference());
        payload.put("action", "PAYMENT_METHOD_UPDATE_REQUIRED");
        payload.put(
                "message",
                "Customer must pay renewal checkout with a new card to update payment method"
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_UPDATED)
                        .eventReference(
                                "subscription.updated:payment_method_update_required:"
                                        + session.getId()
                        )
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private String generateOrderReference() {
        return "renewal_pm_update_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}