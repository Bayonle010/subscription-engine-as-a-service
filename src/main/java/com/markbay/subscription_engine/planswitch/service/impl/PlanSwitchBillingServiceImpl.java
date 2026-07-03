package com.markbay.subscription_engine.planswitch.service.impl;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.billing.service.BillingFeeService;
import com.markbay.subscription_engine.common.exception.BadRequestException;
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
import com.markbay.subscription_engine.nomba.support.NombaMoneyFormatter;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.payment.enums.PaymentProvider;
import com.markbay.subscription_engine.payment.enums.PaymentStatus;
import com.markbay.subscription_engine.payment.repository.PaymentRepository;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.planswitch.entity.PlanSwitchRequest;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchStatus;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchBillingService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSwitchBillingServiceImpl implements PlanSwitchBillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BillingFeeService billingFeeService;
    private final LedgerPostingService ledgerPostingService;
    private final NombaTokenizedCardChargeGateway tokenizedCardChargeGateway;
    private final NombaMoneyFormatter nombaMoneyFormatter;

    @Override
    @Transactional
    public void chargeImmediatePlanSwitch(
            PlanSwitchRequest request
    ) {
        if (request.getChargeAmount() == null
                || request.getChargeAmount().signum() <= 0) {
            return;
        }

        Subscription subscription = request.getSubscription();
        CustomerPaymentMethod paymentMethod = subscription.getPaymentMethod();

        if (paymentMethod == null || !hasText(paymentMethod.getProviderTokenKey())) {
            throw new BadRequestException(
                    "Subscription has no tokenized card payment method for immediate plan switch"
            );
        }

        String orderReference = "plan_switch_" + UUID.randomUUID()
                .toString()
                .replace("-", "");

        Invoice invoice = createProrationInvoice(
                request,
                orderReference
        );

        request.setInvoice(invoice);
        request.setBillingReference(invoice.getBillingReference());
        request.setStatus(PlanSwitchStatus.PAYMENT_PENDING);

        NombaTokenizedCardChargeRequest chargeRequest =
                new NombaTokenizedCardChargeRequest(
                        paymentMethod.getProviderTokenKey(),
                        new NombaTokenizedCardOrder(
                                nombaMoneyFormatter.toCheckoutAmount(request.getChargeAmount()),
                                request.getCurrency(),
                                orderReference,
                                subscription.getCustomer().getEmail(),
                                subscription.getCustomer().getId().toString(),
                                null,
                                null
                        )
                );

        NombaTokenizedCardChargeResult chargeResult =
                tokenizedCardChargeGateway.chargeTokenizedCard(chargeRequest);

        if (!chargeResult.success()) {
            request.setStatus(PlanSwitchStatus.FAILED);
            request.setFailureReason(resolveFailureReason(chargeResult));

            Payment failedPayment = createFailedPayment(
                    request,
                    invoice,
                    paymentMethod,
                    orderReference,
                    chargeResult
            );

            request.setPayment(failedPayment);

            log.warn(
                    "Immediate plan switch charge failed. planSwitchRequestId={}, subscriptionId={}, orderReference={}, status={}, reason={}",
                    request.getId(),
                    subscription.getId(),
                    orderReference,
                    chargeResult.status(),
                    chargeResult.message()
            );

            return;
        }

        BillingFeeResult feeResult =
                billingFeeService.calculateFee(
                        request.getChargeAmount(),
                        request.getCurrency()
                );

        Payment payment = createSuccessfulPayment(
                request,
                invoice,
                paymentMethod,
                orderReference,
                chargeResult,
                feeResult
        );

        LedgerPostingResult ledgerPostingResult =
                ledgerPostingService.postProrationPayment(
                        subscription,
                        payment,
                        feeResult
                );

        payment.setLedgerTransactionRef(ledgerPostingResult.transactionRef());

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setAmountPaid(invoice.getAmountDue());
        invoice.setPaidAt(Instant.now());

        request.setPayment(payment);

        log.info(
                "Immediate plan switch proration charge succeeded. planSwitchRequestId={}, subscriptionId={}, invoiceId={}, paymentId={}, amount={}, currency={}",
                request.getId(),
                subscription.getId(),
                invoice.getId(),
                payment.getId(),
                request.getChargeAmount(),
                request.getCurrency()
        );
    }

    private Invoice createProrationInvoice(
            PlanSwitchRequest request,
            String orderReference
    ) {
        String billingReference = "proration:" + request.getSubscription().getId() + ":" + orderReference;

        Invoice invoice = Invoice.builder()
                .tenant(request.getTenant())
                .customer(request.getSubscription().getCustomer())
                .subscription(request.getSubscription())
                .status(InvoiceStatus.OPEN)
                .billingReason(InvoiceBillingReason.PRORATION)
                .billingReference(billingReference)
                .amountDue(request.getChargeAmount())
                .amountPaid(java.math.BigDecimal.ZERO)
                .currency(request.getCurrency())
                .periodStart(request.getCurrentPeriodStart())
                .periodEnd(request.getCurrentPeriodEnd())
                .dueAt(Instant.now())
                .build();

        return invoiceRepository.save(invoice);
    }

    private Payment createSuccessfulPayment(
            PlanSwitchRequest request,
            Invoice invoice,
            CustomerPaymentMethod paymentMethod,
            String orderReference,
            NombaTokenizedCardChargeResult chargeResult,
            BillingFeeResult feeResult
    ) {
        Payment payment = Payment.builder()
                .tenant(request.getTenant())
                .customer(request.getSubscription().getCustomer())
                .subscription(request.getSubscription())
                .invoice(invoice)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.SUCCEEDED)
                .provider(PaymentProvider.NOMBA)
                .amount(request.getChargeAmount())
                .platformFee(feeResult.platformFee())
                .netAmount(feeResult.merchantNetAmount())
                .currency(request.getCurrency())
                .orderReference(orderReference)
                .providerTransactionReference(chargeResult.transactionReference())
                .providerStatus(chargeResult.status())
                .providerRawResponse(chargeResult.rawResponse())
                .paidAt(Instant.now())
                .build();

        return paymentRepository.save(payment);
    }

    private Payment createFailedPayment(
            PlanSwitchRequest request,
            Invoice invoice,
            CustomerPaymentMethod paymentMethod,
            String orderReference,
            NombaTokenizedCardChargeResult chargeResult
    ) {
        Payment payment = Payment.builder()
                .tenant(request.getTenant())
                .customer(request.getSubscription().getCustomer())
                .subscription(request.getSubscription())
                .invoice(invoice)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.FAILED)
                .provider(PaymentProvider.NOMBA)
                .amount(request.getChargeAmount())
                .currency(request.getCurrency())
                .orderReference(orderReference)
                .providerTransactionReference(chargeResult.transactionReference())
                .providerStatus(chargeResult.status())
                .providerRawResponse(chargeResult.rawResponse())
                .failedAt(Instant.now())
                .build();

        return paymentRepository.save(payment);
    }

    private String resolveFailureReason(
            NombaTokenizedCardChargeResult chargeResult
    ) {
        if (chargeResult == null) {
            return "Nomba charge failed";
        }

        if (hasText(chargeResult.message())) {
            return chargeResult.message();
        }

        if (hasText(chargeResult.status())) {
            return chargeResult.status();
        }

        return "Nomba charge failed";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}