package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.customerportal.entity.PaymentRescueCheckoutSession;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.customerportal.enums.PaymentRescueCheckoutStatus;
import com.markbay.subscription_engine.customerportal.repository.PaymentRescueCheckoutSessionRepository;
import com.markbay.subscription_engine.customerportal.service.PaymentRescueCompletionService;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.dunning.enums.DunningCaseStatus;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.payment.repository.PaymentRepository;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.paymentmethod.service.CustomerPaymentMethodService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRescueCompletionServiceImpl implements PaymentRescueCompletionService {

    private final PaymentRescueCheckoutSessionRepository rescueCheckoutSessionRepository;
    private final PaymentRepository paymentRepository;
    private final BillingFeeService billingFeeService;
    private final LedgerPostingService ledgerPostingService;
    private final CustomerPaymentMethodService customerPaymentMethodService;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public void completeSuccessfulRescuePayment(
            String orderReference,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    ) {
        PaymentRescueCheckoutSession rescueSession =
                rescueCheckoutSessionRepository.findByOrderReference(orderReference)
                        .orElseThrow(() -> new BadRequestException("Payment rescue checkout session not found"));

        if (rescueSession.getStatus() == PaymentRescueCheckoutStatus.COMPLETED) {
            log.info(
                    "Payment rescue checkout already completed. orderReference={}",
                    orderReference
            );
            return;
        }

        if (!verifiedTransaction.success()) {
            throw new BadRequestException("Verified transaction is not successful");
        }

        validateAmountAndCurrency(rescueSession, verifiedTransaction);

        if (paymentRepository.findByTenant_IdAndOrderReference(
                rescueSession.getTenant().getId(),
                orderReference
        ).isPresent()) {
            rescueSession.setStatus(PaymentRescueCheckoutStatus.COMPLETED);
            rescueSession.setCompletedAt(Instant.now());
            return;
        }

        Subscription subscription = rescueSession.getSubscription();
        Invoice invoice = rescueSession.getInvoice();

        Instant paidAt = Instant.now();

        CustomerPaymentMethod updatedPaymentMethod =
                updateSubscriptionPaymentMethodIfTokenExists(
                        subscription,
                        paymentData,
                        verifiedTransaction.rawResponse()
                );

        BillingFeeResult feeResult = billingFeeService.calculateFee(
                invoice.getAmountDue(),
                invoice.getCurrency()
        );

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setAmountPaid(feeResult.grossAmount());
        invoice.setPaidAt(paidAt);

        Payment payment = Payment.builder()
                .tenant(rescueSession.getTenant())
                .customer(rescueSession.getCustomer())
                .subscription(subscription)
                .invoice(invoice)
                .checkoutSession(null)
                .paymentMethod(
                        updatedPaymentMethod != null
                                ? updatedPaymentMethod
                                : subscription.getPaymentMethod()
                )
                .status(PaymentStatus.SUCCEEDED)
                .provider(PaymentProvider.NOMBA)
                .amount(feeResult.grossAmount())
                .platformFee(feeResult.platformFee())
                .netAmount(feeResult.merchantNetAmount())
                .currency(feeResult.currency())
                .orderReference(orderReference)
                .providerTransactionReference(resolveProviderTransactionReference(
                        verifiedTransaction,
                        orderReference
                ))
                .providerStatus(verifiedTransaction.status())
                .providerRawResponse(verifiedTransaction.rawResponse())
                .paidAt(paidAt)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        LedgerPostingResult ledgerPostingResult =
                ledgerPostingService.postRenewalSubscriptionPayment(
                        subscription,
                        savedPayment,
                        feeResult
                );

        savedPayment.setLedgerTransactionRef(ledgerPostingResult.transactionRef());

        paymentRepository.save(savedPayment);

        recoverSubscription(subscription, invoice);
        recoverDunningCase(rescueSession.getDunningCase(), paidAt);
        markPortalSessionUsed(rescueSession.getPortalSession(), paidAt);

        rescueSession.setStatus(PaymentRescueCheckoutStatus.COMPLETED);
        rescueSession.setCompletedAt(paidAt);
        rescueSession.setFailureReason(null);

        recordPaymentRecoveredEvents(
                subscription,
                invoice,
                savedPayment,
                ledgerPostingResult
        );

        if (updatedPaymentMethod != null) {
            recordPaymentMethodUpdatedEvent(subscription);
        }

        log.info(
                "Payment rescue completed successfully. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}, paymentId={}, orderReference={}",
                rescueSession.getTenant().getId(),
                rescueSession.getCustomer().getId(),
                subscription.getId(),
                invoice.getId(),
                savedPayment.getId(),
                orderReference
        );
    }

    @Override
    @Transactional
    public void markRescuePaymentFailed(
            String orderReference,
            String reason
    ) {
        rescueCheckoutSessionRepository.findByOrderReference(orderReference)
                .ifPresent(rescueSession -> {
                    rescueSession.setStatus(PaymentRescueCheckoutStatus.FAILED);
                    rescueSession.setFailedAt(Instant.now());
                    rescueSession.setFailureReason(reason);

                    log.warn(
                            "Payment rescue checkout failed. orderReference={}, reason={}",
                            orderReference,
                            reason
                    );
                });
    }

    private CustomerPaymentMethod updateSubscriptionPaymentMethodIfTokenExists(
            Subscription subscription,
            NombaWebhookPaymentData paymentData,
            String providerData
    ) {
        if (paymentData == null || !hasText(paymentData.tokenKey())) {
            return null;
        }

        CustomerPaymentMethod paymentMethod =
                customerPaymentMethodService.findOrCreateCardPaymentMethod(
                        subscription.getTenant(),
                        subscription.getCustomer(),
                        paymentData,
                        providerData
                );

        subscription.setPaymentMethod(paymentMethod);

        return paymentMethod;
    }

    private void recoverSubscription(
            Subscription subscription,
            Invoice invoice
    ) {
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        if (invoice.getPeriodStart() != null) {
            subscription.setCurrentPeriodStart(invoice.getPeriodStart());
        }

        if (invoice.getPeriodEnd() != null) {
            subscription.setCurrentPeriodEnd(invoice.getPeriodEnd());
        }
    }

    private void recoverDunningCase(
            DunningCase dunningCase,
            Instant recoveredAt
    ) {
        if (dunningCase == null) {
            return;
        }

        dunningCase.setStatus(DunningCaseStatus.RECOVERED);
        dunningCase.setRecoveredAt(recoveredAt);
        dunningCase.setNextRetryAt(null);
        dunningCase.setLastFailureReason(null);
    }

    private void markPortalSessionUsed(
            CustomerPortalSession portalSession,
            Instant usedAt
    ) {
        if (portalSession == null) {
            return;
        }

        portalSession.setStatus(CustomerPortalSessionStatus.USED);
        portalSession.setUsedAt(usedAt);
    }

    private void validateAmountAndCurrency(
            PaymentRescueCheckoutSession rescueSession,
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        BigDecimal verifiedAmount = verifiedTransaction.amount();

        if (verifiedAmount != null
                && verifiedAmount.compareTo(rescueSession.getAmount()) != 0) {
            throw new BadRequestException("Verified transaction amount does not match invoice amount");
        }

        if (hasText(verifiedTransaction.currency())
                && !verifiedTransaction.currency().equalsIgnoreCase(rescueSession.getCurrency())) {
            throw new BadRequestException("Verified transaction currency does not match invoice currency");
        }
    }

    private void recordPaymentRecoveredEvents(
            Subscription subscription,
            Invoice invoice,
            Payment payment,
            LedgerPostingResult ledgerPostingResult
    ) {
        Map<String, String> payload = buildPaymentPayload(
                subscription,
                invoice,
                payment,
                ledgerPostingResult
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.INVOICE_PAID)
                        .eventReference("invoice.paid:" + invoice.getId())
                        .aggregateType("invoice")
                        .aggregateId(invoice.getId().toString())
                        .payload(payload)
                        .build()
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.PAYMENT_SUCCEEDED)
                        .eventReference("payment.succeeded:" + payment.getId())
                        .aggregateType("payment")
                        .aggregateId(payment.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private void recordPaymentMethodUpdatedEvent(Subscription subscription) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("customerName", buildCustomerName(
                subscription.getCustomer().getFirstName(),
                subscription.getCustomer().getLastName()
        ));
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("paymentMethodId", subscription.getPaymentMethod().getId().toString());

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.PAYMENT_METHOD_UPDATED)
                        .eventReference("payment_method.updated:" + subscription.getPaymentMethod().getId())
                        .aggregateType("payment_method")
                        .aggregateId(subscription.getPaymentMethod().getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private Map<String, String> buildPaymentPayload(
            Subscription subscription,
            Invoice invoice,
            Payment payment,
            LedgerPostingResult ledgerPostingResult
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
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("invoiceNumber", invoice.getInvoiceNumber());
        payload.put("paymentId", payment.getId().toString());
        payload.put("orderReference", payment.getOrderReference());
        payload.put("providerTransactionReference", safe(payment.getProviderTransactionReference()));
        payload.put("ledgerTransactionRef", ledgerPostingResult.transactionRef());
        payload.put("amount", payment.getAmount().toPlainString());
        payload.put("platformFee", payment.getPlatformFee().toPlainString());
        payload.put("netAmount", payment.getNetAmount().toPlainString());
        payload.put("currency", payment.getCurrency());

        return payload;
    }

    private String resolveProviderTransactionReference(
            NombaVerifiedTransactionResult verifiedTransaction,
            String fallback
    ) {
        if (verifiedTransaction != null && hasText(verifiedTransaction.transactionReference())) {
            return verifiedTransaction.transactionReference();
        }

        return fallback;
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}