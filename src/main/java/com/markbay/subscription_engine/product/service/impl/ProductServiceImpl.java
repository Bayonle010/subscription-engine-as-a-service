package com.markbay.subscription_engine.product.service;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.product.dto.CreateProductRequest;
import com.markbay.subscription_engine.product.dto.ProductResponse;
import com.markbay.subscription_engine.product.dto.UpdateProductRequest;
import com.markbay.subscription_engine.product.entity.Product;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import com.markbay.subscription_engine.product.repository.ProductRepository;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final AuthenticatedTenantProvider authenticatedTenantProvider;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        String productName = request.name().trim();

        boolean productExists = productRepository.existsByTenant_IdAndNameIgnoreCase(
                tenantId,
                productName
        );

        if (productExists) {
            throw new ConflictException("Product with this name already exists");
        }

        Product product = Product.builder()
                .tenant(tenant)
                .name(productName)
                .description(cleanText(request.description()))
                .status(ProductStatus.ACTIVE)
                .build();

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(
            Long page,
            Long pageSize,
            ProductStatus status
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Pageable pageable = PaginationAdapters.createRecentFirstPageRequest(
                page,
                pageSize
        );

        Page<Product> products;

        if (status == null) {
            products = productRepository.findAllByTenant_Id(
                    tenantId,
                    pageable
            );
        } else {
            products = productRepository.findAllByTenant_IdAndStatus(
                    tenantId,
                    status,
                    pageable
            );
        }

        return products.map(ProductResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID productId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Product product = findProductByIdAndTenant(productId, tenantId);

        return ProductResponse.from(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(
            UUID productId,
            UpdateProductRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Product product = findProductByIdAndTenant(productId, tenantId);

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BadRequestException("Archived products cannot be updated");
        }

        if (hasText(request.name())) {
            String newName = request.name().trim();

            boolean nameChanged = !product.getName().equalsIgnoreCase(newName);

            if (nameChanged) {
                boolean productExists =
                        productRepository.existsByTenant_IdAndNameIgnoreCaseAndIdNot(
                                tenantId,
                                newName,
                                productId
                        );

                if (productExists) {
                    throw new ConflictException("Product with this name already exists");
                }

                product.setName(newName);
            }
        }

        if (request.description() != null) {
            product.setDescription(cleanText(request.description()));
        }

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse archiveProduct(UUID productId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Product product = findProductByIdAndTenant(productId, tenantId);

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            return ProductResponse.from(product);
        }

        product.setStatus(ProductStatus.ARCHIVED);
        product.setArchivedAt(Instant.now());

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    private Product findProductByIdAndTenant(UUID productId, UUID tenantId) {
        return productRepository.findByIdAndTenant_Id(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String cleanedValue = value.trim();

        return cleanedValue.isBlank() ? null : cleanedValue;
    }
}