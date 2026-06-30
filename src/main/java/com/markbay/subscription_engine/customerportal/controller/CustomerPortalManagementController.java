package com.markbay.subscription_engine.customerportal.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.customerportal.dto.CreateCustomerPortalManagementLinkRequest;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalActionResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalInvoiceResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalManagementLinkResponse;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalSubscriptionResponse;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
public class CustomerPortalManagementController {

    private final CustomerPortalManagementService customerPortalManagementService;

    @PostMapping("/management-links")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<CustomerPortalManagementLinkResponse>> createManagementLink(
            @Valid @RequestBody CreateCustomerPortalManagementLinkRequest request
    ) {
        CustomerPortalManagementLinkResponse response =
                customerPortalManagementService.createManagementLink(request.subscriptionId());

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer portal management link created successfully",
                        response
                )
        );
    }

    @GetMapping("/sessions/{token}/subscription")
    public ResponseEntity<ApiResponse<CustomerPortalSubscriptionResponse>> getSubscription(
            @PathVariable String token
    ) {
        CustomerPortalSubscriptionResponse response =
                customerPortalManagementService.getSubscription(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer subscription retrieved successfully",
                        response
                )
        );
    }

    @GetMapping("/sessions/{token}/invoices")
    public ResponseEntity<ApiResponse<List<CustomerPortalInvoiceResponse>>> listInvoices(
            @PathVariable String token
    ) {
        List<CustomerPortalInvoiceResponse> response =
                customerPortalManagementService.listInvoices(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer invoices retrieved successfully",
                        response
                )
        );
    }

    @PostMapping("/sessions/{token}/cancel-at-period-end")
    public ResponseEntity<ApiResponse<CustomerPortalActionResponse>> cancelAtPeriodEnd(
            @PathVariable String token
    ) {
        CustomerPortalActionResponse response =
                customerPortalManagementService.cancelAtPeriodEnd(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription scheduled for cancellation successfully",
                        response
                )
        );
    }

    @PostMapping("/sessions/{token}/cancel-now")
    public ResponseEntity<ApiResponse<CustomerPortalActionResponse>> cancelNow(
            @PathVariable String token
    ) {
        CustomerPortalActionResponse response =
                customerPortalManagementService.cancelNow(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription cancelled successfully",
                        response
                )
        );
    }

    @PostMapping("/sessions/{token}/resume")
    public ResponseEntity<ApiResponse<CustomerPortalActionResponse>> resumeCancellation(
            @PathVariable String token
    ) {
        CustomerPortalActionResponse response =
                customerPortalManagementService.resumeCancellation(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription cancellation resumed successfully",
                        response
                )
        );
    }
}