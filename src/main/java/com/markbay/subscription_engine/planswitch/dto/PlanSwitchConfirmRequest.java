package com.markbay.subscription_engine.planswitch.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlanSwitchConfirmRequest(
        @NotNull(message = "New plan ID is required")
        UUID newPlanId
) {
}