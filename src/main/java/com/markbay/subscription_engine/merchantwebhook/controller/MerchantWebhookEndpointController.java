package com.markbay.subscription_engine.merchantwebhook.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.merchantwebhook.dto.CreateMerchantWebhookEndpointRequest;
import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookEndpointResponse;
import com.markbay.subscription_engine.merchantwebhook.dto.UpdateMerchantWebhookEndpointRequest;
import com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookEndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/merchant-webhook-endpoints")
public class MerchantWebhookEndpointController {

    private final MerchantWebhookEndpointService endpointService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER')")
    @PostMapping
    public ResponseEntity<ApiResponse<MerchantWebhookEndpointResponse>> createEndpoint(
            @Valid @RequestBody CreateMerchantWebhookEndpointRequest request
    ) {
        MerchantWebhookEndpointResponse response =
                endpointService.createEndpoint(request);

        return ResponseEntity.status(201).body(
                ResponseUtil.success(
                        "Merchant webhook endpoint created successfully",
                        response
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MerchantWebhookEndpointResponse>>> listEndpoints() {
        List<MerchantWebhookEndpointResponse> response =
                endpointService.listEndpoints();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant webhook endpoints retrieved successfully",
                        response
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER')")
    @PatchMapping("/{endpointId}")
    public ResponseEntity<ApiResponse<MerchantWebhookEndpointResponse>> updateEndpoint(
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateMerchantWebhookEndpointRequest request
    ) {
        MerchantWebhookEndpointResponse response =
                endpointService.updateEndpoint(endpointId, request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant webhook endpoint updated successfully",
                        response
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PatchMapping("/{endpointId}/disable")
    public ResponseEntity<ApiResponse<MerchantWebhookEndpointResponse>> disableEndpoint(
            @PathVariable UUID endpointId
    ) {
        MerchantWebhookEndpointResponse response =
                endpointService.disableEndpoint(endpointId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant webhook endpoint disabled successfully",
                        response
                )
        );
    }
}