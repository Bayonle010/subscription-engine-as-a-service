package com.markbay.subscription_engine.plan.dto;

import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.BillingInterval;
import com.markbay.subscription_engine.plan.enums.PlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        UUID productId,
        String productName,
        String name,
        String description,
        BigDecimal amount,
        String currency,
        BillingInterval billingInterval,
        Integer billingIntervalCount,
        Integer trialDays,
        List<String> features,
        PlanStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getTenant().getId(),
                plan.getTenant().getId(),
                plan.getProduct().getId(),
                plan.getProduct().getName(),
                plan.getName(),
                plan.getDescription(),
                plan.getAmount(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getBillingIntervalCount(),
                plan.getTrialDays(),
                plan.getFeatures(),
                plan.getStatus(),
                plan.getArchivedAt(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}