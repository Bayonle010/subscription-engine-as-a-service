package com.markbay.subscription_engine.planswitch.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.planswitch.dto.ProrationCalculation;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchDirection;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchMode;
import com.markbay.subscription_engine.planswitch.service.ProrationCalculationService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class ProrationCalculationServiceImpl
        implements ProrationCalculationService {

    @Override
    public ProrationCalculation calculate(
            Subscription subscription,
            Plan newPlan
    ) {
        validate(subscription, newPlan);

        Instant now = Instant.now();

        boolean sameInterval = isSameBillingInterval(subscription, newPlan);

        if (!sameInterval) {
            return new ProrationCalculation(
                    PlanSwitchMode.PERIOD_END,
                    PlanSwitchDirection.INTERVAL_CHANGE,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    remainingSeconds(subscription, now),
                    totalPeriodSeconds(subscription),
                    subscription.getCurrentPeriodEnd(),
                    "Billing interval changes are scheduled for the end of the current period"
            );
        }

        int priceComparison = newPlan.getAmount().compareTo(subscription.getAmount());

        if (priceComparison == 0) {
            return new ProrationCalculation(
                    PlanSwitchMode.IMMEDIATE,
                    PlanSwitchDirection.SAME_PRICE,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    remainingSeconds(subscription, now),
                    totalPeriodSeconds(subscription),
                    now,
                    "Same-price plan switch will be applied immediately"
            );
        }

        if (priceComparison < 0) {
            return new ProrationCalculation(
                    PlanSwitchMode.PERIOD_END,
                    PlanSwitchDirection.DOWNGRADE,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    remainingSeconds(subscription, now),
                    totalPeriodSeconds(subscription),
                    subscription.getCurrentPeriodEnd(),
                    "Downgrade will be applied at the end of the current billing period"
            );
        }

        long remainingSeconds = remainingSeconds(subscription, now);
        long totalPeriodSeconds = totalPeriodSeconds(subscription);

        if (remainingSeconds <= 0 || totalPeriodSeconds <= 0) {
            return new ProrationCalculation(
                    PlanSwitchMode.IMMEDIATE,
                    PlanSwitchDirection.UPGRADE,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    newPlan.getAmount().subtract(subscription.getAmount()).setScale(4, RoundingMode.HALF_UP),
                    remainingSeconds,
                    totalPeriodSeconds,
                    now,
                    "Subscription period has ended, full difference will be charged"
            );
        }

        BigDecimal remainingFraction = BigDecimal.valueOf(remainingSeconds)
                .divide(BigDecimal.valueOf(totalPeriodSeconds), MathContext.DECIMAL64);

        BigDecimal oldUnusedCredit = subscription.getAmount()
                .multiply(remainingFraction)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal newRemainingCost = newPlan.getAmount()
                .multiply(remainingFraction)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal chargeAmount = newRemainingCost
                .subtract(oldUnusedCredit)
                .max(BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);

        return new ProrationCalculation(
                PlanSwitchMode.IMMEDIATE,
                PlanSwitchDirection.UPGRADE,
                oldUnusedCredit,
                chargeAmount,
                remainingSeconds,
                totalPeriodSeconds,
                now,
                "Upgrade will be applied immediately after prorated charge succeeds"
        );
    }

    private void validate(
            Subscription subscription,
            Plan newPlan
    ) {
        if (subscription == null) {
            throw new BadRequestException("Subscription is required");
        }

        if (newPlan == null) {
            throw new BadRequestException("New plan is required");
        }

        if (subscription.getPlan().getId().equals(newPlan.getId())) {
            throw new BadRequestException("Subscription is already on this plan");
        }

        if (!subscription.getCurrency().equalsIgnoreCase(newPlan.getCurrency())) {
            throw new BadRequestException("Cannot switch plans with different currencies");
        }

        if (subscription.getCurrentPeriodStart() == null
                || subscription.getCurrentPeriodEnd() == null) {
            throw new BadRequestException("Subscription has no active billing period");
        }
    }

    private boolean isSameBillingInterval(
            Subscription subscription,
            Plan newPlan
    ) {
        int subscriptionCount = subscription.getBillingIntervalCount() == null
                || subscription.getBillingIntervalCount() <= 0
                ? 1
                : subscription.getBillingIntervalCount();

        int newPlanCount = newPlan.getBillingIntervalCount() == null
                || newPlan.getBillingIntervalCount() <= 0
                ? 1
                : newPlan.getBillingIntervalCount();

        return subscription.getBillingInterval() == newPlan.getBillingInterval()
                && subscriptionCount == newPlanCount;
    }

    private long remainingSeconds(
            Subscription subscription,
            Instant now
    ) {
        if (subscription.getCurrentPeriodEnd().isBefore(now)) {
            return 0;
        }

        return Duration.between(now, subscription.getCurrentPeriodEnd()).toSeconds();
    }

    private long totalPeriodSeconds(Subscription subscription) {
        return Duration.between(
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd()
        ).toSeconds();
    }
}