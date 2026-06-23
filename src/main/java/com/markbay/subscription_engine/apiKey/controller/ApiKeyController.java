package com.markbay.subscription_engine.apiKey.controller;


import com.markbay.subscription_engine.apiKey.dto.ApiKeyResponse;
import com.markbay.subscription_engine.apiKey.dto.CreateApiKeyRequest;
import com.markbay.subscription_engine.apiKey.service.ApiKeyService;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PreAuthorize("hasAnyRole('OWNER', 'DEVELOPER')")
    @PostMapping
    public ApiResponse<ApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        ApiKeyResponse response = apiKeyService.createApiKey(request);

        return ResponseUtil.success(
                0,
                "API key created successfully. Copy the secret key now. It will not be shown again.",
                null,
                response,
                null
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'DEVELOPER')")
    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> listApiKeys() {
        List<ApiKeyResponse> response = apiKeyService.listApiKeys();

        return ResponseUtil.success(
                0,
                "API keys retrieved successfully",
                null,
                response,
                null
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'DEVELOPER')")
    @PostMapping("/{apiKeyId}/revoke")
    public ApiResponse<ApiKeyResponse> revokeApiKey(
            @PathVariable UUID apiKeyId
    ) {
        ApiKeyResponse response = apiKeyService.revokeApiKey(apiKeyId);

        return ResponseUtil.success(
                0,
                "API key revoked successfully",
                null,
                response,
                null
        );
    }
}