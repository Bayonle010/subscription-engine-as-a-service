package com.markbay.subscription_engine.planswitch.service;

import com.markbay.subscription_engine.planswitch.entity.PlanSwitchRequest;

public interface PlanSwitchBillingService {

    void chargeImmediatePlanSwitch(
            PlanSwitchRequest planSwitchRequest
    );
}