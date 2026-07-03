package com.markbay.subscription_engine.planswitch.dto;

import com.markbay.subscription_engine.planswitch.enums.PlanSwitchDirection;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchMode;

import java.math.BigDecimal;
import java.time.Instant;

public record ProrationCalculation(
        PlanSwitchMode mode,
        PlanSwitchDirection direction,
        BigDecimal creditAmount,
        BigDecimal chargeAmount,
        long remainingSeconds,
        long totalPeriodSeconds,
        Instant effectiveAt,
        String message
) {
}