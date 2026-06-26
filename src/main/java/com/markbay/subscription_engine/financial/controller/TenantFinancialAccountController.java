package com.markbay.subscription_engine.financial.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.financial.dto.SetupTenantFinancialAccountRequest;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountBalanceResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialAccountResponse;
import com.markbay.subscription_engine.financial.dto.TenantFinancialSetupResponse;
import com.markbay.subscription_engine.financial.service.TenantFinancialAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenant/financial-account")
public class TenantFinancialAccountController {

    private final TenantFinancialAccountService financialAccountService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TenantFinancialSetupResponse>> setupFinancialAccount(
            @Valid @RequestBody(required = false) SetupTenantFinancialAccountRequest request
    ) {
        TenantFinancialSetupResponse response =
                financialAccountService.setupFinancialAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ResponseUtil.success(
                        0,
                        "Tenant financial setup processed successfully",
                        null,
                        response,
                        null
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping
    public ResponseEntity<ApiResponse<TenantFinancialAccountResponse>> getFinancialAccount() {
        TenantFinancialAccountResponse response =
                financialAccountService.getCurrentTenantFinancialAccount();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Tenant financial account retrieved successfully",
                        null,
                        response,
                        null
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<TenantFinancialAccountBalanceResponse>> getBalance() {
        TenantFinancialAccountBalanceResponse response =
                financialAccountService.getCurrentTenantSubAccountBalance();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Tenant sub-account balance retrieved successfully",
                        null,
                        response,
                        null
                )
        );
    }
}