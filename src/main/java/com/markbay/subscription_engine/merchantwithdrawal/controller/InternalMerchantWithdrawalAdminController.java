package com.markbay.subscription_engine.merchantwithdrawal.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalMerchantWithdrawalAdminController {

    private final MerchantWithdrawalVerificationService verificationService;

    @PostMapping("/{withdrawalId}/reconcile")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Object>> reconcileWithdrawal(
            @PathVariable UUID withdrawalId
    ) {
        verificationService.reconcileProcessingWithdrawal(withdrawalId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant withdrawal reconciliation completed successfully",
                        null
                )
        );
    }
}