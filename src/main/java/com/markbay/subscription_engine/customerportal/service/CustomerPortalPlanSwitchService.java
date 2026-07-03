package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;

public interface CustomerPortalPlanSwitchService {

    PlanSwitchPreviewResponse previewPlanSwitch(
            String rawToken,
            PlanSwitchPreviewRequest request
    );

    PlanSwitchResponse confirmPlanSwitch(
            String rawToken,
            PlanSwitchConfirmRequest request
    );

    PlanSwitchResponse getCurrentPlanSwitch(
            String rawToken
    );

    PlanSwitchResponse cancelPlanSwitch(
            String rawToken
    );
}