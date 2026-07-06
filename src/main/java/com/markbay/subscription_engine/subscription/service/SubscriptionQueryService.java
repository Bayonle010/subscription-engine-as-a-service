package com.markbay.subscription_engine.subscription.service;

import com.markbay.subscription_engine.subscription.dto.response.SubscriptionAnalyticsResponse;
import com.markbay.subscription_engine.subscription.dto.response.SubscriptionResponse;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionQueryService {

    Page<SubscriptionResponse> getSubscriptions(
            SubscriptionStatus status,
            Long page,
            Long pageSize
    );

    SubscriptionResponse getSubscriptionById(UUID subscriptionId);

    SubscriptionAnalyticsResponse getSubscriptionAnalytics();
}