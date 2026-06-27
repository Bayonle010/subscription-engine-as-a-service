package com.markbay.subscription_engine.subscriptioncheckout.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.subscriptioncheckout.dto.CreateSubscriptionCheckoutSessionRequest;
import com.markbay.subscription_engine.subscriptioncheckout.dto.SubscriptionCheckoutSessionResponse;
import com.markbay.subscription_engine.subscriptioncheckout.service.SubscriptionCheckoutSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/subscription-checkout-sessions")
public class SubscriptionCheckoutSessionController {

    private final SubscriptionCheckoutSessionService checkoutSessionService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'API_CLIENT')")
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionCheckoutSessionResponse>> createCheckoutSession(
            @Valid @RequestBody CreateSubscriptionCheckoutSessionRequest request
    ) {
        SubscriptionCheckoutSessionResponse response =
                checkoutSessionService.createCheckoutSession(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ResponseUtil.success(
                        "Subscription checkout session created successfully",
                        response
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SubscriptionCheckoutSessionResponse>> getCheckoutSession(
            @PathVariable UUID sessionId
    ) {
        SubscriptionCheckoutSessionResponse response =
                checkoutSessionService.getCheckoutSession(sessionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription checkout session retrieved successfully",
                        response
                )
        );
    }
}