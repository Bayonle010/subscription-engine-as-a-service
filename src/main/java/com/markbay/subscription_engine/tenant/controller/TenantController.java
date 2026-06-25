package com.markbay.subscription_engine.tenant.controller;


import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.tenant.dto.TenantResponse;
import com.markbay.subscription_engine.tenant.dto.UpdateTenantRequest;
import com.markbay.subscription_engine.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService tenantService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping("/me")
    public ApiResponse<TenantResponse> getCurrentTenant() {
        TenantResponse response = tenantService.getCurrentTenant();

        return ResponseUtil.success(
                0,
                "Tenant retrieved successfully",
                null,
                response,
                null
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PatchMapping("/me")
    public ApiResponse<TenantResponse> updateCurrentTenant(
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        TenantResponse response = tenantService.updateCurrentTenant(request);

        return ResponseUtil.success(
                0,
                "Tenant updated successfully",
                null,
                response,
                null
        );
    }
}