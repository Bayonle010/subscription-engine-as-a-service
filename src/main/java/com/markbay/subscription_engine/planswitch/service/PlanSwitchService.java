package com.markbay.subscription_engine.planswitch.service;

import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;

import java.util.UUID;

public interface PlanSwitchService {

    PlanSwitchPreviewResponse previewPlanSwitch(
            UUID subscriptionId,
            PlanSwitchPreviewRequest request
    );

    PlanSwitchResponse confirmPlanSwitch(
            UUID subscriptionId,
            PlanSwitchConfirmRequest request
    );

    PlanSwitchResponse getCurrentPlanSwitch(
            UUID subscriptionId
    );

    PlanSwitchResponse cancelScheduledPlanSwitch(
            UUID subscriptionId
    );

    void applyDueScheduledPlanSwitch(
            UUID planSwitchRequestId
    );

    void applyDueScheduledPlanSwitchForSubscription(
            UUID subscriptionId
    );
}