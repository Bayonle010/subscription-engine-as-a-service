package com.markbay.subscription_engine.payoutaccount.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupRequest;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupResponse;
import com.markbay.subscription_engine.payoutaccount.dto.CreatePayoutAccountRequest;
import com.markbay.subscription_engine.payoutaccount.dto.MerchantPayoutAccountResponse;
import com.markbay.subscription_engine.payoutaccount.service.MerchantPayoutAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payout-accounts")
@RequiredArgsConstructor
public class MerchantPayoutAccountController {

    private final MerchantPayoutAccountService payoutAccountService;

    @PostMapping("/lookup")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<BankAccountLookupResponse>> lookupBankAccount(
            @Valid @RequestBody BankAccountLookupRequest request
    ) {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Bank account verified successfully",
                        payoutAccountService.lookupBankAccount(request)
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','API_CLIENT')")
    public ResponseEntity<ApiResponse<MerchantPayoutAccountResponse>> createPayoutAccount(
            @Valid @RequestBody CreatePayoutAccountRequest request
    ) {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payout account created successfully",
                        payoutAccountService.createPayoutAccount(request)
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<MerchantPayoutAccountResponse>>> listPayoutAccounts() {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payout accounts retrieved successfully",
                        payoutAccountService.listPayoutAccounts()
                )
        );
    }

    @PatchMapping("/{payoutAccountId}/disable")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','API_CLIENT')")
    public ResponseEntity<ApiResponse<MerchantPayoutAccountResponse>> disablePayoutAccount(
            @PathVariable UUID payoutAccountId
    ) {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Payout account disabled successfully",
                        payoutAccountService.disablePayoutAccount(payoutAccountId)
                )
        );
    }
}