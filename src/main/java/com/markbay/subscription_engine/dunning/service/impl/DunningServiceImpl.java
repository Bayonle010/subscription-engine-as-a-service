package com.markbay.subscription_engine.dunning.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import com.markbay.subscription_engine.dunning.config.DunningProperties;
import com.markbay.subscription_engine.dunning.entity.DunningAttempt;
import com.markbay.subscription_engine.dunning.entity.DunningCase;
import com.markbay.subscription_engine.dunning.enums.DunningAttemptStatus;
import com.markbay.subscription_engine.dunning.enums.DunningCaseStatus;
import com.markbay.subscription_engine.dunning.repository.DunningAttemptRepository;
import com.markbay.subscription_engine.dunning.repository.DunningCaseRepository;
import com.markbay.subscription_engine.dunning.service.DunningService;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
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
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class DunningServiceImpl implements DunningService {

    private final DunningProperties dunningProperties;
    private final DunningCaseRepository dunningCaseRepository;
    private final DunningAttemptRepository dunningAttemptRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final BillingFeeService billingFeeService;
    private final LedgerPostingService ledgerPostingService;
    private final EventOutboxService eventOutboxService;
    private final NombaTokenizedCardChargeGateway tokenizedCardChargeGateway;

    @Override
    @Transactional
    public void openCaseForFailedRenewal(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt failedAttempt,
            String billingReference,
            String failureReason
    ) {
        DunningCase existingCase = dunningCaseRepository.findByInvoice_Id(invoice.getId())
                .orElse(null);

        if (existingCase != null) {
            existingCase.setLastFailedAt(Instant.now());
            existingCase.setLastFailureReason(failureReason);
            existingCase.setNextRetryAt(resolveNextRetryAt(existingCase.getRetryCount() + 1));

            log.info(
                    "Existing dunning case updated. dunningCaseId={}, subscriptionId={}, invoiceId={}",
                    existingCase.getId(),
                    subscription.getId(),
                    invoice.getId()
            );

            return;
        }

        Instant now = Instant.now();

        DunningCase dunningCase = DunningCase.builder()
                .tenant(subscription.getTenant())
                .customer(subscription.getCustomer())
                .subscription(subscription)
                .invoice(invoice)
                .status(DunningCaseStatus.OPEN)
                .retryCount(0)
                .maxRetryAttempts(dunningProperties.getMaxRetryAttempts())
                .firstFailedAt(now)
                .lastFailedAt(now)
                .nextRetryAt(resolveNextRetryAt(1))
                .graceEndsAt(now.plus(dunningProperties.getGracePeriodDays(), ChronoUnit.DAYS))
                .lastFailureReason(failureReason)
                .build();

        DunningCase savedCase = dunningCaseRepository.save(dunningCase);

        scheduleDunningAttempt(savedCase, 1);

        recordPaymentFailedEvent(
                subscription,
                invoice,
                failedAttempt,
                billingReference,
                failureReason
        );

        log.info(
                "Dunning case opened. tenantId={}, subscriptionId={}, invoiceId={}, dunningCaseId={}, nextRetryAt={}, graceEndsAt={}",
                subscription.getTenant().getId(),
                subscription.getId(),
                invoice.getId(),
                savedCase.getId(),
                savedCase.getNextRetryAt(),
                savedCase.getGraceEndsAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueDunningCaseIds(int batchSize) {
        return dunningCaseRepository.findDueDunningCaseIds(
                List.of(DunningCaseStatus.OPEN, DunningCaseStatus.RETRYING),
                Instant.now(),
                PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional
    public void processDunningCase(UUID dunningCaseId) {
        DunningCase dunningCase = dunningCaseRepository.findByIdForUpdate(dunningCaseId)
                .orElseThrow(() -> new IllegalArgumentException("Dunning case not found"));

        if (!canProcess(dunningCase)) {
            return;
        }

        if (dunningCase.getRetryCount() >= dunningCase.getMaxRetryAttempts()) {
            exhaustDunningCase(dunningCase, "Maximum retry attempts reached");
            return;
        }

        int nextAttemptNumber = dunningCase.getRetryCount() + 1;

        DunningAttempt dunningAttempt = dunningAttemptRepository
                .findByDunningCase_IdAndAttemptNumber(dunningCase.getId(), nextAttemptNumber)
                .orElseGet(() -> scheduleDunningAttempt(dunningCase, nextAttemptNumber));

        processRetryAttempt(dunningCase, dunningAttempt);
    }

    private void processRetryAttempt(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt
    ) {
        Subscription subscription = dunningCase.getSubscription();
        Invoice invoice = dunningCase.getInvoice();

        CustomerPaymentMethod paymentMethod = subscription.getPaymentMethod();

        if (!isUsableCardPaymentMethod(paymentMethod)) {
            failDunningAttempt(
                    dunningCase,
                    dunningAttempt,
                    "Subscription does not have an active reusable card payment method"
            );
            return;
        }

        String attemptReference = buildDunningAttemptReference(
                dunningCase,
                dunningAttempt
        );

        PaymentAttempt paymentAttempt = createPaymentAttempt(
                dunningCase,
                dunningAttempt,
                attemptReference
        );

        dunningAttempt.setStatus(DunningAttemptStatus.PROCESSING);
        dunningAttempt.setPaymentAttempt(paymentAttempt);

        try {
            NombaTokenizedCardChargeResult chargeResult =
                    tokenizedCardChargeGateway.chargeTokenizedCard(
                            buildNombaChargeRequest(
                                    subscription,
                                    paymentMethod,
                                    attemptReference
                            )
                    );

            if (chargeResult.success()) {
                recoverDunningCase(
                        dunningCase,
                        dunningAttempt,
                        paymentAttempt,
                        chargeResult,
                        attemptReference
                );

                return;
            }

            failDunningAttemptWithProviderResponse(
                    dunningCase,
                    dunningAttempt,
                    paymentAttempt,
                    chargeResult.status(),
                    chargeResult.message(),
                    chargeResult.rawResponse()
            );

        } catch (Exception exception) {
            failDunningAttemptWithProviderResponse(
                    dunningCase,
                    dunningAttempt,
                    paymentAttempt,
                    null,
                    exception.getMessage(),
                    null
            );
        }
    }

    private PaymentAttempt createPaymentAttempt(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt,
            String attemptReference
    ) {
        PaymentAttempt paymentAttempt = PaymentAttempt.builder()
                .tenant(dunningCase.getTenant())
                .customer(dunningCase.getCustomer())
                .subscription(dunningCase.getSubscription())
                .invoice(dunningCase.getInvoice())
                .paymentMethod(dunningCase.getSubscription().getPaymentMethod())
                .attemptReference(attemptReference)
                .status(PaymentAttemptStatus.PROCESSING)
                .provider(PaymentProvider.NOMBA)
                .amount(dunningCase.getInvoice().getAmountDue())
                .currency(dunningCase.getInvoice().getCurrency())
                .attemptedAt(Instant.now())
                .build();

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(paymentAttempt);

        log.info(
                "Dunning payment attempt created. dunningCaseId={}, dunningAttemptId={}, paymentAttemptId={}, attemptReference={}",
                dunningCase.getId(),
                dunningAttempt.getId(),
                savedAttempt.getId(),
                attemptReference
        );

        return savedAttempt;
    }

    private void recoverDunningCase(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt,
            PaymentAttempt paymentAttempt,
            NombaTokenizedCardChargeResult chargeResult,
            String attemptReference
    ) {
        Instant paidAt = Instant.now();

        Subscription subscription = dunningCase.getSubscription();
        Invoice invoice = dunningCase.getInvoice();

        BillingFeeResult feeResult = billingFeeService.calculateFee(
                invoice.getAmountDue(),
                invoice.getCurrency()
        );

        paymentAttempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        paymentAttempt.setProviderStatus(chargeResult.status());
        paymentAttempt.setProviderTransactionReference(resolveProviderTransactionReference(
                chargeResult,
                attemptReference
        ));
        paymentAttempt.setProviderRawResponse(chargeResult.rawResponse());
        paymentAttempt.setFailureReason(null);
        paymentAttempt.setSucceededAt(paidAt);

        dunningAttempt.setStatus(DunningAttemptStatus.SUCCEEDED);
        dunningAttempt.setProcessedAt(paidAt);
        dunningAttempt.setFailureReason(null);

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
                .orderReference(attemptReference)
                .providerTransactionReference(resolveProviderTransactionReference(
                        chargeResult,
                        attemptReference
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

        dunningCase.setStatus(DunningCaseStatus.RECOVERED);
        dunningCase.setRecoveredAt(paidAt);
        dunningCase.setNextRetryAt(null);
        dunningCase.setLastFailureReason(null);

        recordRecoveryEvents(
                subscription,
                invoice,
                savedPayment,
                ledgerPostingResult
        );

        log.info(
                "Dunning case recovered. dunningCaseId={}, subscriptionId={}, invoiceId={}, paymentId={}, newPeriodEnd={}",
                dunningCase.getId(),
                subscription.getId(),
                invoice.getId(),
                savedPayment.getId(),
                newPeriodEnd
        );
    }

    private void failDunningAttemptWithProviderResponse(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt,
            PaymentAttempt paymentAttempt,
            String providerStatus,
            String failureReason,
            String rawResponse
    ) {
        Instant failedAt = Instant.now();

        paymentAttempt.setStatus(PaymentAttemptStatus.FAILED);
        paymentAttempt.setProviderStatus(providerStatus);
        paymentAttempt.setProviderRawResponse(rawResponse);
        paymentAttempt.setFailureReason(failureReason);
        paymentAttempt.setFailedAt(failedAt);

        dunningAttempt.setStatus(DunningAttemptStatus.FAILED);
        dunningAttempt.setProcessedAt(failedAt);
        dunningAttempt.setFailureReason(failureReason);

        dunningCase.setRetryCount(dunningCase.getRetryCount() + 1);
        dunningCase.setStatus(DunningCaseStatus.RETRYING);
        dunningCase.setLastFailedAt(failedAt);
        dunningCase.setLastFailureReason(failureReason);

        if (dunningCase.getRetryCount() >= dunningCase.getMaxRetryAttempts()) {
            exhaustDunningCase(dunningCase, failureReason);
            return;
        }

        int nextAttemptNumber = dunningCase.getRetryCount() + 1;

        dunningCase.setNextRetryAt(resolveNextRetryAt(nextAttemptNumber));

        scheduleDunningAttempt(dunningCase, nextAttemptNumber);

        recordPaymentFailedEvent(
                dunningCase.getSubscription(),
                dunningCase.getInvoice(),
                paymentAttempt,
                paymentAttempt.getAttemptReference(),
                failureReason
        );

        log.warn(
                "Dunning retry failed. dunningCaseId={}, retryCount={}, nextRetryAt={}, reason={}",
                dunningCase.getId(),
                dunningCase.getRetryCount(),
                dunningCase.getNextRetryAt(),
                failureReason
        );
    }

    private void failDunningAttempt(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt,
            String reason
    ) {
        dunningAttempt.setStatus(DunningAttemptStatus.FAILED);
        dunningAttempt.setProcessedAt(Instant.now());
        dunningAttempt.setFailureReason(reason);

        dunningCase.setRetryCount(dunningCase.getRetryCount() + 1);
        dunningCase.setLastFailedAt(Instant.now());
        dunningCase.setLastFailureReason(reason);

        if (dunningCase.getRetryCount() >= dunningCase.getMaxRetryAttempts()) {
            exhaustDunningCase(dunningCase, reason);
            return;
        }

        int nextAttemptNumber = dunningCase.getRetryCount() + 1;

        dunningCase.setStatus(DunningCaseStatus.RETRYING);
        dunningCase.setNextRetryAt(resolveNextRetryAt(nextAttemptNumber));

        scheduleDunningAttempt(dunningCase, nextAttemptNumber);
    }

    private void exhaustDunningCase(
            DunningCase dunningCase,
            String reason
    ) {
        Instant now = Instant.now();

        dunningCase.setStatus(DunningCaseStatus.EXHAUSTED);
        dunningCase.setExhaustedAt(now);
        dunningCase.setNextRetryAt(null);
        dunningCase.setLastFailureReason(reason);

        dunningCase.getInvoice().setStatus(InvoiceStatus.UNCOLLECTIBLE);

        if (dunningProperties.isCancelAfterFinalFailure()) {
            dunningCase.getSubscription().setStatus(SubscriptionStatus.CANCELLED);
            dunningCase.getSubscription().setCancelledAt(now);
            dunningCase.setCancelledAt(now);

            recordSubscriptionCancelledEvent(dunningCase);
        }

        log.warn(
                "Dunning case exhausted. dunningCaseId={}, subscriptionId={}, invoiceId={}, reason={}",
                dunningCase.getId(),
                dunningCase.getSubscription().getId(),
                dunningCase.getInvoice().getId(),
                reason
        );
    }

    private DunningAttempt scheduleDunningAttempt(
            DunningCase dunningCase,
            int attemptNumber
    ) {
        Instant scheduledAt = resolveNextRetryAt(attemptNumber);

        DunningAttempt attempt = DunningAttempt.builder()
                .tenant(dunningCase.getTenant())
                .dunningCase(dunningCase)
                .attemptNumber(attemptNumber)
                .status(DunningAttemptStatus.SCHEDULED)
                .scheduledAt(scheduledAt)
                .build();

        return dunningAttemptRepository.save(attempt);
    }

    private boolean canProcess(DunningCase dunningCase) {
        return dunningCase.getStatus() == DunningCaseStatus.OPEN
                || dunningCase.getStatus() == DunningCaseStatus.RETRYING;
    }

    private Instant resolveNextRetryAt(int attemptNumber) {
        List<Integer> retryDelays = dunningProperties.getRetryDelaysHours();

        int delayHours;

        if (retryDelays == null || retryDelays.isEmpty()) {
            delayHours = 24;
        } else if (attemptNumber <= retryDelays.size()) {
            delayHours = retryDelays.get(attemptNumber - 1);
        } else {
            delayHours = retryDelays.get(retryDelays.size() - 1);
        }

        return Instant.now().plus(delayHours, ChronoUnit.HOURS);
    }

    private NombaTokenizedCardChargeRequest buildNombaChargeRequest(
            Subscription subscription,
            CustomerPaymentMethod paymentMethod,
            String attemptReference
    ) {
        return new NombaTokenizedCardChargeRequest(
                paymentMethod.getProviderTokenKey(),
                new NombaTokenizedCardOrder(
                        subscription.getAmount()
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString(),
                        subscription.getCurrency(),
                        attemptReference,
                        subscription.getCustomer().getEmail(),
                        subscription.getCustomer().getId().toString(),
                        null,
                        null
                )
        );
    }

    private void recordRecoveryEvents(
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

    private void recordPaymentFailedEvent(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt attempt,
            String billingReference,
            String reason
    ) {
        if (attempt == null) {
            return;
        }

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
        payload.put("paymentAttemptId", attempt.getId().toString());
        payload.put("billingReference", billingReference);
        payload.put("amount", attempt.getAmount().toPlainString());
        payload.put("currency", attempt.getCurrency());
        payload.put("reason", reason == null ? "Payment failed" : reason);

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

    private void recordSubscriptionCancelledEvent(DunningCase dunningCase) {
        Subscription subscription = dunningCase.getSubscription();

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
        payload.put("invoiceId", dunningCase.getInvoice().getId().toString());
        payload.put("invoiceNumber", dunningCase.getInvoice().getInvoiceNumber());
        payload.put("reason", dunningCase.getLastFailureReason());

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

    private boolean isUsableCardPaymentMethod(CustomerPaymentMethod paymentMethod) {
        return paymentMethod != null
                && paymentMethod.getType() == PaymentMethodType.CARD
                && paymentMethod.getStatus() == PaymentAuthorizationStatus.ACTIVE
                && paymentMethod.isReusable()
                && hasText(paymentMethod.getProviderTokenKey());
    }

    private String buildDunningAttemptReference(
            DunningCase dunningCase,
            DunningAttempt dunningAttempt
    ) {
        return "dunning:"
                + dunningCase.getId()
                + ":attempt:"
                + dunningAttempt.getAttemptNumber();
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}