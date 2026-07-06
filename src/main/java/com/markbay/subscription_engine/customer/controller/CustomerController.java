package com.markbay.subscription_engine.customer.controller;

import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.customer.dto.response.CustomerAnalyticsResponse;
import com.markbay.subscription_engine.customer.dto.response.CustomerResponse;
import com.markbay.subscription_engine.customer.enums.CustomerStatus;
import com.markbay.subscription_engine.customer.service.CustomerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerQueryService customerQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getCustomers(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize
    ) {
        Page<CustomerResponse> response =
                customerQueryService.getCustomers(
                        status,
                        page,
                        pageSize
                );

        return ResponseEntity.ok(
                ResponseUtil.success(
                        00,
                        "Customers fetched successfully",
                        null,
                        response.getContent(),
                        PaginationAdapters.toMeta(response)
                )
        );
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(
            @PathVariable UUID customerId
    ) {
        CustomerResponse response =
                customerQueryService.getCustomerById(customerId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer fetched successfully",
                        response
                )
        );
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<CustomerAnalyticsResponse>> getCustomerAnalytics() {
        CustomerAnalyticsResponse response =
                customerQueryService.getCustomerAnalytics();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer analytics fetched successfully",
                        response
                )
        );
    }
}