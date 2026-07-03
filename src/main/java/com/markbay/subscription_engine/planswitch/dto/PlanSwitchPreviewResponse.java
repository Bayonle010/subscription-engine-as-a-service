package com.markbay.subscription_engine.planswitch.dto;

import com.markbay.subscription_engine.planswitch.enums.PlanSwitchDirection;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanSwitchPreviewResponse(
        UUID subscriptionId,
        UUID oldPlanId,
        String oldPlanName,
        UUID newPlanId,
        String newPlanName,
        PlanSwitchMode mode,
        PlanSwitchDirection direction,
        BigDecimal oldAmount,
        BigDecimal newAmount,
        BigDecimal creditAmount,
        BigDecimal chargeAmount,
        String currency,
        long remainingSeconds,
        long totalPeriodSeconds,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant effectiveAt,
        String message
) {
}