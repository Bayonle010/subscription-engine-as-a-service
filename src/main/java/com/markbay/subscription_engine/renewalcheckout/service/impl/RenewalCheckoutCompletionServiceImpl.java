package com.markbay.subscription_engine.renewalcheckout.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.payment.repository.PaymentRepository;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.paymentmethod.service.CustomerPaymentMethodService;
import com.markbay.subscription_engine.renewalcheckout.entity.RenewalCheckoutSession;
import com.markbay.subscription_engine.renewalcheckout.enums.RenewalCheckoutStatus;
import com.markbay.subscription_engine.renewalcheckout.repository.RenewalCheckoutSessionRepository;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutCompletionService;
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
public class RenewalCheckoutCompletionServiceImpl
        implements RenewalCheckoutCompletionService {

    private final RenewalCheckoutSessionRepository renewalCheckoutSessionRepository;
    private final CustomerPaymentMethodService customerPaymentMethodService;
    private final PaymentRepository paymentRepository;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public void completeSuccessfulRenewalCheckout(
            String orderReference,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    ) {
        if (!hasText(orderReference)) {
            throw new BadRequestException("Order reference is required");
        }

        RenewalCheckoutSession session =
                renewalCheckoutSessionRepository.findByOrderReference(orderReference)
                        .orElseThrow(() -> new BadRequestException(
                                "Renewal checkout session not found"
                        ));

        if (session.getStatus() == RenewalCheckoutStatus.COMPLETED) {
            log.info(
                    "Renewal checkout already completed. renewalCheckoutSessionId={}, orderReference={}",
                    session.getId(),
                    orderReference
            );

            return;
        }

        if (verifiedTransaction == null || !verifiedTransaction.success()) {
            throw new BadRequestException("Verified transaction is not successful");
        }

        if (paymentData == null || !hasText(paymentData.tokenKey())) {
            throw new BadRequestException(
                    "Nomba tokenized card token is missing. Payment method cannot be updated."
            );
        }

        Subscription subscription = session.getSubscription();
        Invoice invoice = session.getInvoice();

        CustomerPaymentMethod oldPaymentMethod = subscription.getPaymentMethod();

        CustomerPaymentMethod newPaymentMethod =
                customerPaymentMethodService.findOrCreateCardPaymentMethod(
                        session.getTenant(),
                        session.getCustomer(),
                        paymentData,
                        verifiedTransaction.rawResponse()
                );

        Instant now = Instant.now();

        Payment payment = findOrCreateSuccessfulPayment(
                session,
                invoice,
                subscription,
                newPaymentMethod,
                verifiedTransaction,
                paymentData,
                now
        );

        markInvoicePaid(invoice, now);

        activateSubscriptionForPaidRenewal(
                subscription,
                invoice,
                newPaymentMethod,
                now
        );

        markRenewalCheckoutCompleted(
                session,
                oldPaymentMethod,
                newPaymentMethod,
                verifiedTransaction,
                now
        );

        recordInvoicePaidEvent(subscription, invoice, payment);
        recordPaymentSucceededEvent(subscription, invoice, payment);
        recordPaymentMethodUpdatedEvent(
                subscription,
                oldPaymentMethod,
                newPaymentMethod
        );

        log.info(
                "Renewal checkout completed successfully. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}, orderReference={}",
                session.getTenant().getId(),
                session.getCustomer().getId(),
                subscription.getId(),
                invoice.getId(),
                orderReference
        );
    }

    private Payment findOrCreateSuccessfulPayment(
            RenewalCheckoutSession session,
            Invoice invoice,
            Subscription subscription,
            CustomerPaymentMethod paymentMethod,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData,
            Instant paidAt
    ) {
        return paymentRepository
                .findByTenant_IdAndOrderReference(
                        session.getTenant().getId(),
                        session.getOrderReference()
                )
                .orElseGet(() -> paymentRepository.save(
                        Payment.builder()
                                .tenant(session.getTenant())
                                .customer(session.getCustomer())
                                .subscription(subscription)
                                .invoice(invoice)
                                .paymentMethod(paymentMethod)
                                .status(PaymentStatus.SUCCEEDED)
                                .provider(PaymentProvider.NOMBA)
                                .amount(session.getAmount())
                                .platformFee(BigDecimal.ZERO)
                                .netAmount(session.getAmount())
                                .currency(session.getCurrency())
                                .orderReference(session.getOrderReference())
                                .providerTransactionReference(resolveTransactionReference(
                                        verifiedTransaction,
                                        paymentData,
                                        session.getOrderReference()
                                ))
                                .providerStatus(verifiedTransaction.status())
                                .providerRawResponse(verifiedTransaction.rawResponse())
                                .paidAt(paidAt)
                                .build()
                ));
    }

    private void markInvoicePaid(
            Invoice invoice,
            Instant paidAt
    ) {
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setAmountPaid(invoice.getAmountDue());
        invoice.setPaidAt(paidAt);
    }

    private void activateSubscriptionForPaidRenewal(
            Subscription subscription,
            Invoice invoice,
            CustomerPaymentMethod newPaymentMethod,
            Instant fulfilledAt
    ) {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPaymentMethod(newPaymentMethod);

        subscription.setCurrentPeriodStart(invoice.getPeriodStart());
        subscription.setCurrentPeriodEnd(invoice.getPeriodEnd());

        subscription.setPaymentMethodUpdateRequested(false);
        subscription.setPaymentMethodUpdateFulfilledAt(fulfilledAt);
    }

    private void markRenewalCheckoutCompleted(
            RenewalCheckoutSession session,
            CustomerPaymentMethod oldPaymentMethod,
            CustomerPaymentMethod newPaymentMethod,
            NombaVerifiedTransactionResult verifiedTransaction,
            Instant completedAt
    ) {
        session.setOldPaymentMethod(oldPaymentMethod);
        session.setNewPaymentMethod(newPaymentMethod);
        session.setStatus(RenewalCheckoutStatus.COMPLETED);
        session.setCompletedAt(completedAt);
        session.setFailureReason(null);
        session.setProviderStatus(verifiedTransaction.status());
        session.setProviderTransactionReference(resolveTransactionReference(
                verifiedTransaction,
                null,
                session.getOrderReference()
        ));
        session.setProviderRawResponse(verifiedTransaction.rawResponse());
    }

    private void recordInvoicePaidEvent(
            Subscription subscription,
            Invoice invoice,
            Payment payment
    ) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("amount", invoice.getAmountDue().toPlainString());
        payload.put("currency", invoice.getCurrency());
        payload.put("status", invoice.getStatus().name());
        payload.put("billingReason", invoice.getBillingReason().name());
        payload.put("action", "RENEWAL_CHECKOUT_PAID");
        payload.put("message", "Renewal invoice paid through checkout");

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
    }

    private void recordPaymentSucceededEvent(
            Subscription subscription,
            Invoice invoice,
            Payment payment
    ) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("amount", payment.getAmount().toPlainString());
        payload.put("currency", payment.getCurrency());
        payload.put("orderReference", payment.getOrderReference());
        payload.put("providerTransactionReference", safe(payment.getProviderTransactionReference()));
        payload.put("status", payment.getStatus().name());
        payload.put("action", "RENEWAL_CHECKOUT_PAYMENT_SUCCEEDED");
        payload.put("message", "Renewal checkout payment succeeded");

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

    private void recordPaymentMethodUpdatedEvent(
            Subscription subscription,
            CustomerPaymentMethod oldPaymentMethod,
            CustomerPaymentMethod newPaymentMethod
    ) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("subscriptionStatus", subscription.getStatus().name());
        payload.put(
                "oldPaymentMethodId",
                oldPaymentMethod == null ? "" : oldPaymentMethod.getId().toString()
        );
        payload.put("newPaymentMethodId", newPaymentMethod.getId().toString());
        payload.put("paymentMethodType", newPaymentMethod.getType().name());
        payload.put("paymentMethodStatus", newPaymentMethod.getStatus().name());
        payload.put("cardBrand", safe(newPaymentMethod.getCardBrand()));
        payload.put("cardLast4", safe(newPaymentMethod.getCardLast4()));
        payload.put("action", "PAYMENT_METHOD_UPDATED_ON_RENEWAL");
        payload.put("message", "Customer payment method updated through renewal checkout");

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.PAYMENT_METHOD_UPDATED)
                        .eventReference("payment_method.updated:" + newPaymentMethod.getId())
                        .aggregateType("payment_method")
                        .aggregateId(newPaymentMethod.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private String resolveTransactionReference(
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData,
            String fallback
    ) {
        if (verifiedTransaction != null
                && hasText(verifiedTransaction.transactionReference())) {
            return verifiedTransaction.transactionReference();
        }

        if (paymentData != null
                && hasText(paymentData.transactionReference())) {
            return paymentData.transactionReference();
        }

        return fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}