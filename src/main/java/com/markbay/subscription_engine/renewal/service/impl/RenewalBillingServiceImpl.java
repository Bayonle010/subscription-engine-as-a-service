package com.markbay.subscription_engine.renewal.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import com.markbay.subscription_engine.dunning.service.DunningService;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceBillingReason;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
import com.markbay.subscription_engine.invoice.repository.InvoiceRepository;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardChargeRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardOrder;
import com.markbay.subscription_engine.nomba.dto.response.NombaTokenizedCardChargeResult;
import com.markbay.subscription_engine.nomba.gateway.NombaTokenizedCardChargeGateway;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.payment.repository.PaymentRepository;
import com.markbay.subscription_engine.paymentattempt.entity.PaymentAttempt;
import com.markbay.subscription_engine.paymentattempt.enums.PaymentAttemptStatus;
import com.markbay.subscription_engine.paymentattempt.repository.PaymentAttemptRepository;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentAuthorizationStatus;
import com.markbay.subscription_engine.paymentmethod.enums.PaymentMethodType;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.BillingInterval;
import com.markbay.subscription_engine.renewal.service.RenewalBillingService;
import com.markbay.subscription_engine.renewalcheckout.service.RenewalCheckoutService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
public class RenewalBillingServiceImpl implements RenewalBillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final BillingFeeService billingFeeService;
    private final LedgerPostingService ledgerPostingService;
    private final EventOutboxService eventOutboxService;
    private final NombaTokenizedCardChargeGateway tokenizedCardChargeGateway;
    private final DunningService dunningService;
    private final RenewalCheckoutService renewalCheckoutService;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueRenewalSubscriptionIds(int batchSize) {
        return subscriptionRepository.findDueSubscriptionIds(
                SubscriptionStatus.ACTIVE,
                Instant.now(),
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional
    public void processSubscriptionRenewal(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdForRenewalUpdate(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (!isDueForRenewal(subscription)) {
            return;
        }

        if (subscription.isCancelAtPeriodEnd()) {
            cancelAtPeriodEnd(subscription);
            return;
        }


        String billingReference = buildRenewalBillingReference(subscription);

        if (paymentRepository.findByTenant_IdAndOrderReference(
                subscription.getTenant().getId(),
                billingReference
        ).isPresent()) {
            log.info(
                    "Renewal already paid. subscriptionId={}, billingReference={}",
                    subscription.getId(),
                    billingReference
            );

            return;
        }

        CustomerPaymentMethod paymentMethod = subscription.getPaymentMethod();

        if (!isUsableCardPaymentMethod(paymentMethod)) {
            markRenewalFailedWithoutAttempt(
                    subscription,
                    billingReference,
                    "Subscription does not have an active reusable card payment method"
            );
            return;
        }

        Invoice invoice = getOrCreateRenewalInvoice(subscription, billingReference);

        if (subscription.isPaymentMethodUpdateRequested()) {
            handleRenewalWithPaymentMethodUpdateRequest(subscription, invoice);
            return;
        }

        PaymentAttempt attempt = createPaymentAttempt(
                subscription,
                invoice,
                paymentMethod,
                billingReference
        );

        try {
            NombaTokenizedCardChargeResult chargeResult =
                    tokenizedCardChargeGateway.chargeTokenizedCard(
                            buildNombaChargeRequest(
                                    subscription,
                                    paymentMethod,
                                    billingReference
                            )
                    );

            if (chargeResult.success()) {
                handleSuccessfulRenewal(
                        subscription,
                        invoice,
                        attempt,
                        chargeResult,
                        billingReference
                );

                return;
            }

            handleFailedRenewal(
                    subscription,
                    invoice,
                    attempt,
                    chargeResult.status(),
                    chargeResult.message(),
                    chargeResult.rawResponse(),
                    billingReference
            );

        } catch (Exception exception) {
            handleFailedRenewal(
                    subscription,
                    invoice,
                    attempt,
                    null,
                    exception.getMessage(),
                    null,
                    billingReference
            );
        }
    }

    private Invoice getOrCreateRenewalInvoice(
            Subscription subscription,
            String billingReference
    ) {
        return invoiceRepository.findByBillingReference(billingReference)
                .orElseGet(() -> {
                    Instant dueAt = Instant.now();

                    Invoice invoice = Invoice.builder()
                            .tenant(subscription.getTenant())
                            .customer(subscription.getCustomer())
                            .subscription(subscription)
                            .billingReference(billingReference)
                            .invoiceNumber(generateInvoiceNumber())
                            .status(InvoiceStatus.OPEN)
                            .billingReason(InvoiceBillingReason.RENEWAL)
                            .amountDue(subscription.getAmount())
                            .amountPaid(java.math.BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                            .currency(subscription.getCurrency())
                            .periodStart(subscription.getCurrentPeriodEnd())
                            .periodEnd(calculateNextPeriodEnd(
                                    subscription.getCurrentPeriodEnd(),
                                    subscription.getPlan()
                            ))
                            .dueAt(dueAt)
                            .description("Subscription renewal")
                            .build();

                    Invoice savedInvoice = invoiceRepository.save(invoice);

                    log.info(
                            "Renewal invoice created. tenantId={}, subscriptionId={}, invoiceId={}, billingReference={}",
                            subscription.getTenant().getId(),
                            subscription.getId(),
                            savedInvoice.getId(),
                            billingReference
                    );

                    return savedInvoice;
                });
    }

    private PaymentAttempt createPaymentAttempt(
            Subscription subscription,
            Invoice invoice,
            CustomerPaymentMethod paymentMethod,
            String billingReference
    ) {
        String attemptReference = "attempt_" + UUID.randomUUID().toString().replace("-", "");

        PaymentAttempt attempt = PaymentAttempt.builder()
                .tenant(subscription.getTenant())
                .customer(subscription.getCustomer())
                .subscription(subscription)
                .invoice(invoice)
                .paymentMethod(paymentMethod)
                .attemptReference(attemptReference)
                .status(PaymentAttemptStatus.PROCESSING)
                .provider(PaymentProvider.NOMBA)
                .amount(subscription.getAmount())
                .currency(subscription.getCurrency())
                .attemptedAt(Instant.now())
                .build();

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);

        log.info(
                "Renewal payment attempt created. tenantId={}, subscriptionId={}, invoiceId={}, attemptId={}, billingReference={}",
                subscription.getTenant().getId(),
                subscription.getId(),
                invoice.getId(),
                savedAttempt.getId(),
                billingReference
        );

        return savedAttempt;
    }

    private void handleSuccessfulRenewal(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt attempt,
            NombaTokenizedCardChargeResult chargeResult,
            String billingReference
    ) {
        Instant paidAt = Instant.now();

        BillingFeeResult feeResult = billingFeeService.calculateFee(
                subscription.getAmount(),
                subscription.getCurrency()
        );

        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setProviderStatus(chargeResult.status());
        attempt.setProviderTransactionReference(resolveProviderTransactionReference(
                chargeResult,
                billingReference
        ));
        attempt.setProviderRawResponse(chargeResult.rawResponse());
        attempt.setSucceededAt(paidAt);
        attempt.setFailureReason(null);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setAmountPaid(feeResult.grossAmount());
        invoice.setPaidAt(paidAt);

        Payment payment = Payment.builder()
                .tenant(subscription.getTenant())
                .customer(subscription.getCustomer())
                .subscription(subscription)
                .invoice(invoice)
                .checkoutSession(null)
                .paymentMethod(subscription.getPaymentMethod())
                .status(PaymentStatus.SUCCEEDED)
                .provider(PaymentProvider.NOMBA)
                .amount(feeResult.grossAmount())
                .platformFee(feeResult.platformFee())
                .netAmount(feeResult.merchantNetAmount())
                .currency(feeResult.currency())
                .orderReference(billingReference)
                .providerTransactionReference(resolveProviderTransactionReference(
                        chargeResult,
                        billingReference
                ))
                .providerStatus(chargeResult.status())
                .providerRawResponse(chargeResult.rawResponse())
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

        Instant oldPeriodEnd = subscription.getCurrentPeriodEnd();
        Instant newPeriodEnd = calculateNextPeriodEnd(oldPeriodEnd, subscription.getPlan());

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(oldPeriodEnd);
        subscription.setCurrentPeriodEnd(newPeriodEnd);

        recordRenewalSuccessEvents(
                subscription,
                invoice,
                savedPayment,
                ledgerPostingResult
        );

        log.info(
                "Renewal payment succeeded. tenantId={}, subscriptionId={}, invoiceId={}, paymentId={}, oldPeriodEnd={}, newPeriodEnd={}",
                subscription.getTenant().getId(),
                subscription.getId(),
                invoice.getId(),
                savedPayment.getId(),
                oldPeriodEnd,
                newPeriodEnd
        );
    }

    private void handleFailedRenewal(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt attempt,
            String providerStatus,
            String failureReason,
            String rawResponse,
            String billingReference
    ) {
        Instant failedAt = Instant.now();

        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setProviderStatus(providerStatus);
        attempt.setProviderRawResponse(rawResponse);
        attempt.setFailureReason(failureReason);
        attempt.setFailedAt(failedAt);

        invoice.setStatus(InvoiceStatus.OPEN);

        subscription.setStatus(SubscriptionStatus.PAST_DUE);


        dunningService.openCaseForFailedRenewal(
                subscription,
                invoice,
                attempt,
                billingReference,
                failureReason
        );

        log.warn(
                "Renewal payment failed. tenantId={}, subscriptionId={}, invoiceId={}, attemptId={}, reason={}",
                subscription.getTenant().getId(),
                subscription.getId(),
                invoice.getId(),
                attempt.getId(),
                failureReason
        );
    }

    private void markRenewalFailedWithoutAttempt(
            Subscription subscription,
            String billingReference,
            String reason
    ) {
        subscription.setStatus(SubscriptionStatus.PAST_DUE);

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.PAYMENT_FAILED)
                        .eventReference("payment.failed:" + billingReference)
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(Map.of(
                                "tenantId", subscription.getTenant().getId().toString(),
                                "customerId", subscription.getCustomer().getId().toString(),
                                "customerEmail", subscription.getCustomer().getEmail(),
                                "customerName", buildCustomerName(
                                        subscription.getCustomer().getFirstName(),
                                        subscription.getCustomer().getLastName()
                                ),
                                "subscriptionId", subscription.getId().toString(),
                                "planId", subscription.getPlan().getId().toString(),
                                "planName", subscription.getPlan().getName(),
                                "amount", subscription.getAmount().toPlainString(),
                                "currency", subscription.getCurrency(),
                                "reason", reason
                        ))
                        .build()
        );

        log.warn(
                "Renewal marked failed without payment attempt. subscriptionId={}, reason={}",
                subscription.getId(),
                reason
        );
    }

    private void cancelAtPeriodEnd(Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_CANCELLED)
                        .eventReference("subscription.cancelled:" + subscription.getId())
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(Map.of(
                                "tenantId", subscription.getTenant().getId().toString(),
                                "customerId", subscription.getCustomer().getId().toString(),
                                "customerEmail", subscription.getCustomer().getEmail(),
                                "customerName", buildCustomerName(
                                        subscription.getCustomer().getFirstName(),
                                        subscription.getCustomer().getLastName()
                                ),
                                "subscriptionId", subscription.getId().toString(),
                                "subscriptionStatus", subscription.getStatus().name(),
                                "planId", subscription.getPlan().getId().toString(),
                                "planName", subscription.getPlan().getName()
                        ))
                        .build()
        );

        log.info(
                "Subscription cancelled at period end. subscriptionId={}",
                subscription.getId()
        );
    }

    private void handleRenewalWithPaymentMethodUpdateRequest(
            Subscription subscription,
            Invoice invoice
    ) {
        subscription.setStatus(SubscriptionStatus.PAST_DUE);

        renewalCheckoutService.createCheckoutForPaymentMethodUpdateRenewal(
                subscription,
                invoice
        );

        log.info(
                "Skipped automatic renewal charge because payment method update was requested. tenantId={}, customerId={}, subscriptionId={}, invoiceId={}",
                subscription.getTenant().getId(),
                subscription.getCustomer().getId(),
                subscription.getId(),
                invoice.getId()
        );
    }

    private void recordRenewalSuccessEvents(
            Subscription subscription,
            Invoice invoice,
            Payment payment,
            LedgerPostingResult ledgerPostingResult
    ) {
        Map<String, String> payload = Map.ofEntries(
                Map.entry("tenantId", subscription.getTenant().getId().toString()),
                Map.entry("customerId", subscription.getCustomer().getId().toString()),
                Map.entry("customerEmail", subscription.getCustomer().getEmail()),
                Map.entry("customerName", buildCustomerName(
                        subscription.getCustomer().getFirstName(),
                        subscription.getCustomer().getLastName()
                )),
                Map.entry("subscriptionId", subscription.getId().toString()),
                Map.entry("subscriptionStatus", subscription.getStatus().name()),
                Map.entry("planId", subscription.getPlan().getId().toString()),
                Map.entry("planName", subscription.getPlan().getName()),
                Map.entry("invoiceId", invoice.getId().toString()),
                Map.entry("invoiceNumber", invoice.getInvoiceNumber()),
                Map.entry("paymentId", payment.getId().toString()),
                Map.entry("orderReference", payment.getOrderReference()),
                Map.entry("providerTransactionReference", payment.getProviderTransactionReference()),
                Map.entry("ledgerTransactionRef", ledgerPostingResult.transactionRef()),
                Map.entry("amount", payment.getAmount().toPlainString()),
                Map.entry("platformFee", payment.getPlatformFee().toPlainString()),
                Map.entry("netAmount", payment.getNetAmount().toPlainString()),
                Map.entry("currency", payment.getCurrency())
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

    private void recordPaymentFailedEvent(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt attempt,
            String billingReference,
            String reason
    ) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put(
                "customerName",
                buildCustomerName(
                        subscription.getCustomer().getFirstName(),
                        subscription.getCustomer().getLastName()
                )
        );
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("subscriptionStatus", subscription.getStatus().name());
        payload.put("planId", subscription.getPlan().getId().toString());
        payload.put("planName", subscription.getPlan().getName());
        payload.put("invoiceId", invoice.getId().toString());
        payload.put("invoiceNumber", invoice.getInvoiceNumber());
        payload.put("paymentAttemptId", attempt.getId().toString());
        payload.put("billingReference", billingReference);
        payload.put("amount", attempt.getAmount().toPlainString());
        payload.put("currency", attempt.getCurrency());
        payload.put(
                "reason",
                reason == null ? "Renewal payment failed" : reason
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.PAYMENT_FAILED)
                        .eventReference("payment.failed:" + attempt.getId())
                        .aggregateType("payment_attempt")
                        .aggregateId(attempt.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private NombaTokenizedCardChargeRequest buildNombaChargeRequest(
            Subscription subscription,
            CustomerPaymentMethod paymentMethod,
            String billingReference
    ) {
        return new NombaTokenizedCardChargeRequest(
                paymentMethod.getProviderTokenKey(),
                new NombaTokenizedCardOrder(
                        subscription.getAmount()
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString(),
                        subscription.getCurrency(),
                        billingReference,
                        subscription.getCustomer().getEmail(),
                        subscription.getCustomer().getId().toString(),
                        null,
                        null
                )
        );
    }

    private boolean isDueForRenewal(Subscription subscription) {
        return subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getCurrentPeriodEnd() != null
                && !subscription.getCurrentPeriodEnd().isAfter(Instant.now());
    }

    private boolean isUsableCardPaymentMethod(CustomerPaymentMethod paymentMethod) {
        return paymentMethod != null
                && paymentMethod.getType() == PaymentMethodType.CARD
                && paymentMethod.getStatus() == PaymentAuthorizationStatus.ACTIVE
                && paymentMethod.isReusable()
                && hasText(paymentMethod.getProviderTokenKey());
    }

    private Instant calculateNextPeriodEnd(
            Instant start,
            Plan plan
    ) {
        int count = resolveBillingIntervalCount(plan);
        BillingInterval interval = plan.getBillingInterval();

        return switch (interval) {
            case DAILY -> start.plus(count, ChronoUnit.DAYS);
            case WEEKLY -> start.plus(count, ChronoUnit.WEEKS);
            case MONTHLY -> start.plus(count, ChronoUnit.MONTHS);
            case YEARLY -> start.plus(count, ChronoUnit.YEARS);
            case CUSTOM -> start.plus(count, ChronoUnit.DAYS);
        };
    }

    private int resolveBillingIntervalCount(Plan plan) {
        if (plan.getBillingIntervalCount() == null || plan.getBillingIntervalCount() <= 0) {
            return 1;
        }

        return plan.getBillingIntervalCount();
    }

    private String buildRenewalBillingReference(Subscription subscription) {
        return "renewal:"
                + subscription.getId()
                + ":"
                + subscription.getCurrentPeriodEnd().getEpochSecond();
    }

    private String generateInvoiceNumber() {
        return "inv_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveProviderTransactionReference(
            NombaTokenizedCardChargeResult result,
            String fallback
    ) {
        if (result != null && hasText(result.transactionReference())) {
            return result.transactionReference();
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