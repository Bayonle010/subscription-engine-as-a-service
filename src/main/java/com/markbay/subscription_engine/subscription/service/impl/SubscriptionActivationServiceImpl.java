package com.markbay.subscription_engine.subscription.service.impl;

import com.markbay.subscription_engine.billing.service.InitialSubscriptionBillingService;
import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customer.service.CustomerService;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.paymentmethod.entity.CustomerPaymentMethod;
import com.markbay.subscription_engine.paymentmethod.service.CustomerPaymentMethodService;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.BillingInterval;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import com.markbay.subscription_engine.subscription.service.SubscriptionActivationService;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;
import com.markbay.subscription_engine.subscriptioncheckout.enums.CheckoutSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionActivationServiceImpl implements SubscriptionActivationService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerService customerService;
    private final CustomerPaymentMethodService paymentMethodService;
    private final InitialSubscriptionBillingService initialSubscriptionBillingService;

    @Override
    @Transactional
    public Subscription activateFromSuccessfulCheckout(
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    ) {
        return subscriptionRepository.findByCheckoutSession_Id(checkoutSession.getId())
                .orElseGet(() -> activateNewSubscription(
                        checkoutSession,
                        verifiedTransaction,
                        paymentData
                ));
    }

    private Subscription activateNewSubscription(
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    ) {
        validateCheckoutCanBeActivated(checkoutSession, verifiedTransaction);

        Customer customer = customerService.findOrCreateForCheckout(
                checkoutSession.getTenant(),
                checkoutSession
        );

        CustomerPaymentMethod paymentMethod =
                paymentMethodService.findOrCreateCardPaymentMethod(
                        checkoutSession.getTenant(),
                        customer,
                        paymentData,
                        verifiedTransaction.rawResponse()
                );

        Plan plan = checkoutSession.getPlan();

        Instant now = Instant.now();
        Instant periodEnd = calculateCurrentPeriodEnd(now, plan);

        Subscription subscription = Subscription.builder()
                .tenant(checkoutSession.getTenant())
                .customer(customer)
                .plan(plan)
                .paymentMethod(paymentMethod)
                .checkoutSession(checkoutSession)
                .status(SubscriptionStatus.ACTIVE)
                .amount(plan.getAmount())
                .currency(plan.getCurrency())
                .billingInterval(plan.getBillingInterval())
                .billingIntervalCount(resolveBillingIntervalCount(plan))
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .cancelAtPeriodEnd(false)
                .activatedAt(now)
                .build();

        checkoutSession.setStatus(CheckoutSessionStatus.COMPLETED);
        checkoutSession.setCompletedAt(now);

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        initialSubscriptionBillingService.recordInitialSubscriptionPayment(
                savedSubscription,
                checkoutSession,
                verifiedTransaction
        );

        log.info(
                "Subscription activated. tenantId={}, customerId={}, subscriptionId={}, checkoutSessionId={}",
                savedSubscription.getTenant().getId(),
                savedSubscription.getCustomer().getId(),
                savedSubscription.getId(),
                checkoutSession.getId()
        );

        return savedSubscription;
    }

    private void validateCheckoutCanBeActivated(
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        if (checkoutSession.getStatus() == CheckoutSessionStatus.COMPLETED) {
            return;
        }

        if (checkoutSession.getStatus() != CheckoutSessionStatus.PAYMENT_PENDING) {
            throw new BadRequestException(
                    "Checkout session is not waiting for payment"
            );
        }

        if (verifiedTransaction == null || !verifiedTransaction.success()) {
            throw new BadRequestException(
                    "Nomba transaction verification was not successful"
            );
        }

        if (verifiedTransaction.amount() != null) {
            BigDecimal expectedAmount = checkoutSession.getAmount().stripTrailingZeros();
            BigDecimal paidAmount = verifiedTransaction.amount().stripTrailingZeros();

            if (paidAmount.compareTo(expectedAmount) != 0) {
                throw new BadRequestException(
                        "Verified transaction amount does not match checkout amount"
                );
            }
        }

        if (
                hasText(verifiedTransaction.currency())
                        && !verifiedTransaction.currency().equalsIgnoreCase(checkoutSession.getCurrency())
        ) {
            throw new BadRequestException(
                    "Verified transaction currency does not match checkout currency"
            );
        }
    }

    private Instant calculateCurrentPeriodEnd(Instant start, Plan plan) {
        int count = resolveBillingIntervalCount(plan);

        BillingInterval interval = plan.getBillingInterval();

        return switch (interval) {
            case DAILY -> start.plus(count, ChronoUnit.DAYS);

            case WEEKLY -> start.plus(count * 7L, ChronoUnit.DAYS);

            case MONTHLY -> start
                    .atZone(ZoneOffset.UTC)
                    .plusMonths(count)
                    .toInstant();

            case YEARLY -> start
                    .atZone(ZoneOffset.UTC)
                    .plusYears(count)
                    .toInstant();

            case CUSTOM -> start.plus(count, ChronoUnit.DAYS);
        };
    }

    private int resolveBillingIntervalCount(Plan plan) {
        if (plan.getBillingIntervalCount() == null || plan.getBillingIntervalCount() <= 0) {
            return 1;
        }

        return plan.getBillingIntervalCount();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}