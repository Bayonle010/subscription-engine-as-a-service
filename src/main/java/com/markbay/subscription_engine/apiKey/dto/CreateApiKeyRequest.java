package com.markbay.subscription_engine.apiKey.dto;


import com.markbay.subscription_engine.apiKey.enums.ApiKeyMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApiKeyRequest(

        @NotBlank(message = "API key name is required")
        String name,

        @NotNull(message = "API key mode is required")
        ApiKeyMode mode
) {
}