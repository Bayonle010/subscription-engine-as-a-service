package com.markbay.subscription_engine.merchantwithdrawal.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.MerchantWithdrawalResponse;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant-withdrawals")
@RequiredArgsConstructor
@Validated
public class MerchantWithdrawalController {

    private final MerchantWithdrawalService withdrawalService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','API_CLIENT')")
    public ResponseEntity<ApiResponse<MerchantWithdrawalResponse>> requestWithdrawal(
            @RequestHeader("Idempotency-Key")  String idempotencyKey,
            @Valid @RequestBody CreateMerchantWithdrawalRequest request
    ) {
        MerchantWithdrawalResponse response =
                withdrawalService.requestWithdrawal(
                        idempotencyKey,
                        request
                );

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant withdrawal request processed successfully",
                        response
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<MerchantWithdrawalResponse>>> listWithdrawals() {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant withdrawals retrieved successfully",
                        withdrawalService.listWithdrawals()
                )
        );
    }

    @GetMapping("/{withdrawalId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<MerchantWithdrawalResponse>> getWithdrawal(
            @PathVariable UUID withdrawalId
    ) {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant withdrawal retrieved successfully",
                        withdrawalService.getWithdrawal(withdrawalId)
                )
        );
    }

    @PostMapping("/{withdrawalId}/retry")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<MerchantWithdrawalResponse>> retryWithdrawal(
            @PathVariable UUID withdrawalId
    ) {
        MerchantWithdrawalResponse response =
                withdrawalService.retryWithdrawal(withdrawalId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Merchant withdrawal retry processed successfully",
                        response
                )
        );
    }
}