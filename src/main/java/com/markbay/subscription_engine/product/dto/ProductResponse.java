package com.markbay.subscription_engine.product.dto;

import com.markbay.subscription_engine.product.entity.Product;
import com.markbay.subscription_engine.product.enums.ProductStatus;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID accountId,
        UUID tenantId,
        String name,
        String description,
        ProductStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getTenant().getId(),
                product.getTenant().getId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getArchivedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}