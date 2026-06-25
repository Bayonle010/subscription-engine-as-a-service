package com.markbay.subscription_engine.product.repository;

import com.markbay.subscription_engine.product.entity.Product;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findAllByTenant_Id(UUID tenantId, Pageable pageable);

    Page<Product> findAllByTenant_IdAndStatus(
            UUID tenantId,
            ProductStatus status,
            Pageable pageable
    );

    Optional<Product> findByIdAndTenant_Id(UUID productId, UUID tenantId);

    boolean existsByTenant_IdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenant_IdAndNameIgnoreCaseAndIdNot(
            UUID tenantId,
            String name,
            UUID productId
    );
}