package com.markbay.subscription_engine.planswitch.dto;

import com.markbay.subscription_engine.planswitch.entity.PlanSwitchRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanSwitchResponse(
        UUID id,
        UUID subscriptionId,
        UUID oldPlanId,
        String oldPlanName,
        UUID newPlanId,
        String newPlanName,
        String status,
        String mode,
        String direction,
        BigDecimal creditAmount,
        BigDecimal chargeAmount,
        String currency,
        Instant effectiveAt,
        Instant appliedAt,
        Instant cancelledAt,
        String failureReason,
        UUID invoiceId,
        UUID paymentId
) {
    public static PlanSwitchResponse from(PlanSwitchRequest request) {
        return new PlanSwitchResponse(
                request.getId(),
                request.getSubscription().getId(),
                request.getOldPlan().getId(),
                request.getOldPlan().getName(),
                request.getNewPlan().getId(),
                request.getNewPlan().getName(),
                request.getStatus().name(),
                request.getMode().name(),
                request.getDirection().name(),
                request.getCreditAmount(),
                request.getChargeAmount(),
                request.getCurrency(),
                request.getEffectiveAt(),
                request.getAppliedAt(),
                request.getCancelledAt(),
                request.getFailureReason(),
                request.getInvoice() == null ? null : request.getInvoice().getId(),
                request.getPayment() == null ? null : request.getPayment().getId()
        );
    }
}