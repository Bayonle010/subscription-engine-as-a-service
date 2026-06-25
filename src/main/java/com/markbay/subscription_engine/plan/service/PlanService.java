package com.markbay.subscription_engine.plan.service;

import com.markbay.subscription_engine.plan.dto.CreatePlanRequest;
import com.markbay.subscription_engine.plan.dto.PlanResponse;
import com.markbay.subscription_engine.plan.dto.UpdatePlanRequest;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PlanService {

    PlanResponse createPlan(
            UUID productId,
            CreatePlanRequest request
    );

    Page<PlanResponse> listPlans(
            Long page,
            Long pageSize,
            PlanStatus status
    );

    Page<PlanResponse> listProductPlans(
            UUID productId,
            Long page,
            Long pageSize,
            PlanStatus status
    );

    PlanResponse getPlan(UUID planId);

    PlanResponse updatePlan(
            UUID planId,
            UpdatePlanRequest request
    );

    PlanResponse archivePlan(UUID planId);
}