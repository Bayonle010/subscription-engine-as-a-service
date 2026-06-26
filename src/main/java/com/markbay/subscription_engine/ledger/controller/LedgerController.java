package com.markbay.subscription_engine.ledger.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.ledger.dto.LedgerAccountResponse;
import com.markbay.subscription_engine.ledger.service.LedgerService;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final AuthenticatedTenantProvider authenticatedTenantProvider;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<LedgerAccountResponse>>> listLedgerAccounts() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        List<LedgerAccountResponse> response =
                ledgerService.listTenantLedgerAccounts(tenantId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Ledger accounts retrieved successfully",
                        null,
                        response,
                        null
                )
        );
    }
}