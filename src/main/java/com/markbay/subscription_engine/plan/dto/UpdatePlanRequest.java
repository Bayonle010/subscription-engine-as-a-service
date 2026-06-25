package com.markbay.subscription_engine.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePlanRequest(

        @Size(max = 120, message = "Plan name cannot exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Plan description cannot exceed 1000 characters")
        String description,

        List<@NotBlank(message = "Feature cannot be blank") String> features
) {
}