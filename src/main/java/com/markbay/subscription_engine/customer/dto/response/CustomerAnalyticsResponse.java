package com.markbay.subscription_engine.customer.dto.response;

import java.util.Map;

public record CustomerAnalyticsResponse(
        long totalCustomers,
        Map<String, Long> customersByStatus
) {
}