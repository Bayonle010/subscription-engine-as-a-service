package com.markbay.subscription_engine.planswitch.service;

import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.planswitch.dto.ProrationCalculation;
import com.markbay.subscription_engine.subscription.entity.Subscription;

public interface ProrationCalculationService {

    ProrationCalculation calculate(
            Subscription subscription,
            Plan newPlan
    );

}