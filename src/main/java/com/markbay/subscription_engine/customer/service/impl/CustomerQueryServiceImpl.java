package com.markbay.subscription_engine.customer.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.customer.dto.response.CustomerAnalyticsResponse;
import com.markbay.subscription_engine.customer.dto.response.CustomerResponse;
import com.markbay.subscription_engine.customer.entity.Customer;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;
import com.markbay.subscription_engine.customer.repository.CustomerRepository;
import com.markbay.subscription_engine.customer.service.CustomerQueryService;
import com.markbay.subscription_engine.security.AuthenticatedMerchantProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private final CustomerRepository customerRepository;
    private final AuthenticatedMerchantProvider authenticatedMerchantProvider;

    @Override
    public Page<CustomerResponse> getCustomers(
            CustomerStatus status,
            Long page,
            Long pageSize
    ) {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        Pageable pageable = PaginationAdapters.createRecentFirstPageRequest(
                page,
                pageSize
        );

        Page<Customer> customers = status == null
                ? customerRepository.findAllByTenant_Id(
                tenantId,
                pageable
        )
                : customerRepository.findAllByTenant_IdAndStatus(
                tenantId,
                status,
                pageable
        );

        return customers.map(CustomerResponse::from);
    }

    @Override
    public CustomerResponse getCustomerById(UUID customerId) {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        Customer customer = customerRepository
                .findByIdAndTenant_Id(customerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        return CustomerResponse.from(customer);
    }

    @Override
    public CustomerAnalyticsResponse getCustomerAnalytics() {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        long totalCustomers = customerRepository.countByTenant_Id(tenantId);

        Map<String, Long> customersByStatus = new LinkedHashMap<>();

        for (CustomerStatus status : CustomerStatus.values()) {
            long count = customerRepository.countByTenant_IdAndStatus(
                    tenantId,
                    status
            );

            customersByStatus.put(status.name(), count);
        }

        return new CustomerAnalyticsResponse(
                totalCustomers,
                customersByStatus
        );
    }
}