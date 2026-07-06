package com.markbay.subscription_engine.subscription.dto.response;

import java.util.Map;

public record SubscriptionAnalyticsResponse(
        long totalSubscriptions,
        Map<String, Long> subscriptionsByStatus
) {
}