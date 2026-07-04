package com.markbay.subscription_engine.bank.controller;

import com.markbay.subscription_engine.bank.dto.BankResponse;
import com.markbay.subscription_engine.bank.service.BankService;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<BankResponse>>> syncBanks() {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Banks synced successfully",
                        bankService.syncBanksFromNomba()
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<BankResponse>>> listBanks() {
        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Banks retrieved successfully",
                        bankService.listBanksFromDatabase()
                )
        );
    }
}