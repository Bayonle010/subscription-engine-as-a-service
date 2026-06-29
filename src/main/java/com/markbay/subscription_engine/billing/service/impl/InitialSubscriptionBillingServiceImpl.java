package com.markbay.subscription_engine.billing.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.dto.BillingRecordResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import com.markbay.subscription_engine.billing.service.InitialSubscriptionBillingService;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.invoice.enums.InvoiceBillingReason;
import com.markbay.subscription_engine.invoice.enums.InvoiceStatus;
import com.markbay.subscription_engine.invoice.repository.InvoiceRepository;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.ledger.service.LedgerPostingService;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.payment.repository.PaymentRepository;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitialSubscriptionBillingServiceImpl
        implements InitialSubscriptionBillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BillingFeeService billingFeeService;
    private final LedgerPostingService ledgerPostingService;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public BillingRecordResult recordInitialSubscriptionPayment(
            Subscription subscription,
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        return paymentRepository.findByTenant_IdAndOrderReference(
                        subscription.getTenant().getId(),
                        checkoutSession.getOrderReference()
                )
                .map(this::toExistingBillingRecordResult)
                .orElseGet(() -> createBillingRecords(
                        subscription,
                        checkoutSession,
                        verifiedTransaction
                ));
    }

    private BillingRecordResult createBillingRecords(
            Subscription subscription,
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        try {
            Instant paidAt = Instant.now();

            BillingFeeResult feeResult = billingFeeService.calculateFee(
                    checkoutSession.getAmount(),
                    checkoutSession.getCurrency()
            );

            Invoice invoice = Invoice.builder()
                    .tenant(subscription.getTenant())
                    .customer(subscription.getCustomer())
                    .subscription(subscription)
                    .checkoutSession(checkoutSession)
                    .invoiceNumber(generateInvoiceNumber())
                    .status(InvoiceStatus.PAID)
                    .billingReason(InvoiceBillingReason.INITIAL_SUBSCRIPTION)
                    .amountDue(feeResult.grossAmount())
                    .amountPaid(feeResult.grossAmount())
                    .currency(feeResult.currency())
                    .periodStart(subscription.getCurrentPeriodStart())
                    .periodEnd(subscription.getCurrentPeriodEnd())
                    .dueAt(paidAt)
                    .paidAt(paidAt)
                    .description("Initial subscription payment")
                    .build();

            Invoice savedInvoice = invoiceRepository.save(invoice);

            Payment payment = Payment.builder()
                    .tenant(subscription.getTenant())
                    .customer(subscription.getCustomer())
                    .subscription(subscription)
                    .invoice(savedInvoice)
                    .checkoutSession(checkoutSession)
                    .paymentMethod(subscription.getPaymentMethod())
                    .status(PaymentStatus.SUCCEEDED)
                    .provider(PaymentProvider.NOMBA)
                    .amount(feeResult.grossAmount())
                    .platformFee(feeResult.platformFee())
                    .netAmount(feeResult.merchantNetAmount())
                    .currency(feeResult.currency())
                    .orderReference(checkoutSession.getOrderReference())
                    .providerTransactionReference(resolveProviderTransactionReference(
                            verifiedTransaction,
                            checkoutSession
                    ))
                    .providerStatus(resolveProviderStatus(verifiedTransaction))
                    .providerRawResponse(resolveProviderRawResponse(verifiedTransaction))
                    .paidAt(paidAt)
                    .build();

            Payment savedPayment = paymentRepository.save(payment);

            LedgerPostingResult ledgerPostingResult =
                    ledgerPostingService.postInitialSubscriptionPayment(
                            subscription,
                            savedPayment,
                            feeResult
                    );

            savedPayment.setLedgerTransactionRef(
                    ledgerPostingResult.transactionRef()
            );

            Payment updatedPayment = paymentRepository.save(savedPayment);

            recordOutboxEvents(
                    subscription,
                    savedInvoice,
                    updatedPayment,
                    ledgerPostingResult
            );

            log.info(
                    "Initial subscription billing recorded. tenantId={}, subscriptionId={}, invoiceId={}, paymentId={}, ledgerTransactionRef={}",
                    subscription.getTenant().getId(),
                    subscription.getId(),
                    savedInvoice.getId(),
                    updatedPayment.getId(),
                    updatedPayment.getLedgerTransactionRef()
            );

            return new BillingRecordResult(
                    savedInvoice.getId(),
                    updatedPayment.getId(),
                    ledgerPostingResult.ledgerTransactionId(),
                    savedInvoice.getInvoiceNumber(),
                    updatedPayment.getOrderReference(),
                    updatedPayment.getProviderTransactionReference(),
                    ledgerPostingResult.transactionRef(),
                    feeResult.grossAmount(),
                    feeResult.platformFee(),
                    feeResult.merchantNetAmount(),
                    feeResult.currency()
            );

        } catch (DataIntegrityViolationException exception) {
            log.info(
                    "Initial subscription billing already recorded by another transaction. orderReference={}",
                    checkoutSession.getOrderReference()
            );

            return paymentRepository.findByTenant_IdAndOrderReference(
                            subscription.getTenant().getId(),
                            checkoutSession.getOrderReference()
                    )
                    .map(this::toExistingBillingRecordResult)
                    .orElseThrow(() -> exception);
        }
    }

    private BillingRecordResult toExistingBillingRecordResult(Payment payment) {
        log.info(
                "Initial subscription payment already exists. paymentId={}, orderReference={}",
                payment.getId(),
                payment.getOrderReference()
        );

        return new BillingRecordResult(
                payment.getInvoice().getId(),
                payment.getId(),
                null,
                payment.getInvoice().getInvoiceNumber(),
                payment.getOrderReference(),
                payment.getProviderTransactionReference(),
                payment.getLedgerTransactionRef(),
                payment.getAmount(),
                payment.getPlatformFee(),
                payment.getNetAmount(),
                payment.getCurrency()
        );
    }

    private String generateInvoiceNumber() {
        return "inv_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveProviderTransactionReference(
            NombaVerifiedTransactionResult verifiedTransaction,
            SubscriptionCheckoutSession checkoutSession
    ) {
        if (
                verifiedTransaction != null
                        && hasText(verifiedTransaction.transactionReference())
        ) {
            return verifiedTransaction.transactionReference();
        }

        return checkoutSession.getOrderReference();
    }

    private String resolveProviderStatus(
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        if (verifiedTransaction == null || !hasText(verifiedTransaction.status())) {
            return "SUCCESS";
        }

        return verifiedTransaction.status();
    }

    private String resolveProviderRawResponse(
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        if (verifiedTransaction == null) {
            return null;
        }

        return verifiedTransaction.rawResponse();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void recordOutboxEvents(
            Subscription subscription,
            Invoice invoice,
            Payment payment,
            LedgerPostingResult ledgerPostingResult
    ) {
        var payload = java.util.Map.ofEntries(
                java.util.Map.entry("tenantId", subscription.getTenant().getId().toString()),
                java.util.Map.entry("customerId", subscription.getCustomer().getId().toString()),
                java.util.Map.entry("customerEmail", subscription.getCustomer().getEmail()),
                java.util.Map.entry(
                        "customerName",
                        buildCustomerName(
                                subscription.getCustomer().getFirstName(),
                                subscription.getCustomer().getLastName()
                        )
                ),
                java.util.Map.entry("subscriptionId", subscription.getId().toString()),
                java.util.Map.entry("subscriptionStatus", subscription.getStatus().name()),
                java.util.Map.entry("planId", subscription.getPlan().getId().toString()),
                java.util.Map.entry("planName", subscription.getPlan().getName()),
                java.util.Map.entry("invoiceId", invoice.getId().toString()),
                java.util.Map.entry("invoiceNumber", invoice.getInvoiceNumber()),
                java.util.Map.entry("paymentId", payment.getId().toString()),
                java.util.Map.entry("orderReference", payment.getOrderReference()),
                java.util.Map.entry("providerTransactionReference", payment.getProviderTransactionReference()),
                java.util.Map.entry("ledgerTransactionRef", ledgerPostingResult.transactionRef()),
                java.util.Map.entry("amount", payment.getAmount().toPlainString()),
                java.util.Map.entry("platformFee", payment.getPlatformFee().toPlainString()),
                java.util.Map.entry("netAmount", payment.getNetAmount().toPlainString()),
                java.util.Map.entry("currency", payment.getCurrency())
        );

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_ACTIVATED)
                        .eventReference("subscription.activated:" + subscription.getId())
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
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
}