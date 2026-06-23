package com.markbay.subscription_engine.product.controller;

import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.product.dto.CreateProductRequest;
import com.markbay.subscription_engine.product.dto.ProductResponse;
import com.markbay.subscription_engine.product.dto.UpdateProductRequest;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import com.markbay.subscription_engine.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ResponseUtil.success("Product created successfully", response)
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listProducts(
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) ProductStatus status
    ) {
        Page<ProductResponse> response = productService.listProducts(page, pageSize, status);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        00,
                        "Products retrieved successfully",
                        null,
                        response.getContent(),
                        PaginationAdapters.toMeta(response)
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID productId
    ) {
        ProductResponse response = productService.getProduct(productId);

        return ResponseEntity.ok(
                ResponseUtil.success("Product retrieved successfully", response)
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(productId, request);

        return ResponseEntity.ok(
                ResponseUtil.success("Product updated successfully", response)
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PatchMapping("/{productId}/archive")
    public ResponseEntity<ApiResponse<ProductResponse>> archiveProduct(
            @PathVariable UUID productId
    ) {
        ProductResponse response = productService.archiveProduct(productId);

        return ResponseEntity.ok(
                ResponseUtil.success("Product archived successfully", response)
        );
    }
}