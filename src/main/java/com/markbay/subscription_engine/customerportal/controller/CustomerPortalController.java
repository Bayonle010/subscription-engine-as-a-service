package com.markbay.subscription_engine.customerportal.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.customerportal.dto.CreatePaymentRescueLinkRequest;
import com.markbay.subscription_engine.customerportal.dto.CustomerPortalOverviewResponse;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueCheckoutResponse;
import com.markbay.subscription_engine.customerportal.dto.PaymentRescueLinkResponse;
import com.markbay.subscription_engine.customerportal.service.PaymentRescueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final PaymentRescueService paymentRescueService;

    @PostMapping("/customer-portal/payment-rescue-links")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<PaymentRescueLinkResponse>> createPaymentRescueLink(
            @Valid @RequestBody CreatePaymentRescueLinkRequest request
    ) {
        PaymentRescueLinkResponse response =
                paymentRescueService.createPaymentRescueLinkForInvoice(request.invoiceId());

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payment rescue link created successfully",
                        response
                )
        );
    }

    @GetMapping("/customer-portal/sessions/{token}")
    public ResponseEntity<ApiResponse<CustomerPortalOverviewResponse>> getPortalOverview(
            @PathVariable String token
    ) {
        CustomerPortalOverviewResponse response =
                paymentRescueService.getPortalOverview(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Customer portal session retrieved successfully",
                        response
                )
        );
    }

    @PostMapping("/customer-portal/sessions/{token}/pay")
    public ResponseEntity<ApiResponse<PaymentRescueCheckoutResponse>> createPaymentRescueCheckout(
            @PathVariable String token
    ) {
        PaymentRescueCheckoutResponse response =
                paymentRescueService.createPaymentRescueCheckout(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payment rescue checkout created successfully",
                        response
                )
        );
    }
}