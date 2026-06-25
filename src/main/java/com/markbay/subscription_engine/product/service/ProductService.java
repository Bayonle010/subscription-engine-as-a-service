package com.markbay.subscription_engine.product.service;

import com.markbay.subscription_engine.product.dto.CreateProductRequest;
import com.markbay.subscription_engine.product.dto.ProductResponse;
import com.markbay.subscription_engine.product.dto.UpdateProductRequest;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    Page<ProductResponse> listProducts(
            Long page,
            Long pageSize,
            ProductStatus status
    );

    ProductResponse getProduct(UUID productId);

    ProductResponse updateProduct(
            UUID productId,
            UpdateProductRequest request
    );

    ProductResponse archiveProduct(UUID productId);
}