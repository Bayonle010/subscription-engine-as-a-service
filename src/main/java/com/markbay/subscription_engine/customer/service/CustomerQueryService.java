package com.markbay.subscription_engine.customer.service;

import com.markbay.subscription_engine.customer.dto.response.CustomerAnalyticsResponse;
import com.markbay.subscription_engine.customer.dto.response.CustomerResponse;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface CustomerQueryService {

    Page<CustomerResponse> getCustomers(
            CustomerStatus status,
            Long page,
            Long pageSize
    );

    CustomerResponse getCustomerById(UUID customerId);

    CustomerAnalyticsResponse getCustomerAnalytics();
}