package com.markbay.subscription_engine.reconciliation.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.reconciliation.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class PaymentReconciliationController {

    private final PaymentReconciliationService reconciliationService;

    @PostMapping("/subscription-checkouts/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Void>> reconcileSubscriptionCheckout(
            @PathVariable UUID sessionId
    ) {
        reconciliationService.reconcileSubscriptionCheckoutSession(sessionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription checkout reconciliation completed",
                        null
                )
        );
    }

    @PostMapping("/payment-rescue-checkouts/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Void>> reconcilePaymentRescueCheckout(
            @PathVariable UUID sessionId
    ) {
        reconciliationService.reconcilePaymentRescueCheckoutSession(sessionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payment rescue checkout reconciliation completed",
                        null
                )
        );
    }

    @PostMapping("/webhooks/{eventId}/retry")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Void>> retryWebhook(
            @PathVariable UUID eventId
    ) {
        reconciliationService.retryFailedWebhookEvent(eventId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Webhook retry completed",
                        null
                )
        );
    }

    @PostMapping("/renewal-checkouts/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Void>> reconcileRenewalCheckout(
            @PathVariable UUID sessionId
    ) {
        reconciliationService.reconcileRenewalCheckoutSession(sessionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Renewal checkout reconciliation completed",
                        null
                )
        );
    }
}