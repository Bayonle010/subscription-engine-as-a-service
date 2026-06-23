package com.markbay.subscription_engine.product.dto;

import jakarta.validation.constraints.Size;

public record UpdateProductRequest(

        @Size(max = 120, message = "Product name cannot exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Product description cannot exceed 1000 characters")
        String description
) {
}