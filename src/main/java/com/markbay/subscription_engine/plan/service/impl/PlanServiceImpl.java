package com.markbay.subscription_engine.plan.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ConflictException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.plan.dto.CreatePlanRequest;
import com.markbay.subscription_engine.plan.dto.PlanResponse;
import com.markbay.subscription_engine.plan.dto.UpdatePlanRequest;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import com.markbay.subscription_engine.plan.repository.PlanRepository;
import com.markbay.subscription_engine.plan.service.PlanService;
import com.markbay.subscription_engine.product.entity.Product;
import com.markbay.subscription_engine.product.enums.ProductStatus;
import com.markbay.subscription_engine.product.repository.ProductRepository;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final ProductRepository productRepository;
    private final AuthenticatedTenantProvider authenticatedTenantProvider;

    @Override
    @Transactional
    public PlanResponse createPlan(
            UUID productId,
            CreatePlanRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Product product = productRepository.findByIdAndTenant_Id(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new BadRequestException("Cannot create a plan under an archived product");
        }

        String planName = request.name().trim();

        boolean planExists = planRepository.existsByTenant_IdAndProduct_IdAndNameIgnoreCase(
                tenantId,
                productId,
                planName
        );

        if (planExists) {
            throw new ConflictException("Plan with this name already exists for this product");
        }

        Plan plan = Plan.builder()
                .tenant(product.getTenant())
                .product(product)
                .name(planName)
                .description(cleanText(request.description()))
                .amount(request.amount())
                .currency(request.currency().trim().toUpperCase())
                .billingInterval(request.billingInterval())
                .billingIntervalCount(request.billingIntervalCount())
                .trialDays(resolveTrialDays(request.trialDays()))
                .features(cleanFeatures(request.features()))
                .status(PlanStatus.ACTIVE)
                .build();

        Plan savedPlan = planRepository.save(plan);

        return PlanResponse.from(savedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlanResponse> listPlans(
            Long page,
            Long pageSize,
            PlanStatus status
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Pageable pageable = PaginationAdapters.createRecentFirstPageRequest(
                page,
                pageSize
        );

        Page<Plan> plans;

        if (status == null) {
            plans = planRepository.findAllByTenant_Id(
                    tenantId,
                    pageable
            );
        } else {
            plans = planRepository.findAllByTenant_IdAndStatus(
                    tenantId,
                    status,
                    pageable
            );
        }

        return plans.map(PlanResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlanResponse> listProductPlans(
            UUID productId,
            Long page,
            Long pageSize,
            PlanStatus status
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Product product = productRepository.findByIdAndTenant_Id(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Pageable pageable = PaginationAdapters.createRecentFirstPageRequest(
                page,
                pageSize
        );

        Page<Plan> plans;

        if (status == null) {
            plans = planRepository.findAllByTenant_IdAndProduct_Id(
                    tenantId,
                    product.getId(),
                    pageable
            );
        } else {
            plans = planRepository.findAllByTenant_IdAndProduct_IdAndStatus(
                    tenantId,
                    product.getId(),
                    status,
                    pageable
            );
        }

        return plans.map(PlanResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponse getPlan(UUID planId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Plan plan = findPlanByIdAndTenant(planId, tenantId);

        return PlanResponse.from(plan);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(
            UUID planId,
            UpdatePlanRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Plan plan = findPlanByIdAndTenant(planId, tenantId);

        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new BadRequestException("Archived plans cannot be updated");
        }

        if (hasText(request.name())) {
            String newName = request.name().trim();

            boolean nameChanged = !plan.getName().equalsIgnoreCase(newName);

            if (nameChanged) {
                boolean planExists =
                        planRepository.existsByTenant_IdAndProduct_IdAndNameIgnoreCaseAndIdNot(
                                tenantId,
                                plan.getProduct().getId(),
                                newName,
                                planId
                        );

                if (planExists) {
                    throw new ConflictException("Plan with this name already exists for this product");
                }

                plan.setName(newName);
            }
        }

        if (request.description() != null) {
            plan.setDescription(cleanText(request.description()));
        }

        if (request.features() != null) {
            plan.getFeatures().clear();
            plan.getFeatures().addAll(cleanFeatures(request.features()));
        }

        Plan savedPlan = planRepository.save(plan);

        return PlanResponse.from(savedPlan);
    }

    @Override
    @Transactional
    public PlanResponse archivePlan(UUID planId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Plan plan = findPlanByIdAndTenant(planId, tenantId);

        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            return PlanResponse.from(plan);
        }

        /*
         * Important:
         * We archive instead of deleting.
         *
         * Later, when subscriptions exist:
         * - existing active subscriptions can continue using this plan
         * - new subscriptions should not be allowed to use archived plans
         * - invoices and billing history remain valid
         */
        plan.setStatus(PlanStatus.ARCHIVED);
        plan.setArchivedAt(Instant.now());

        Plan savedPlan = planRepository.save(plan);

        return PlanResponse.from(savedPlan);
    }

    private Plan findPlanByIdAndTenant(UUID planId, UUID tenantId) {
        return planRepository.findByIdAndTenant_Id(planId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
    }

    private Integer resolveTrialDays(Integer trialDays) {
        return trialDays == null ? 0 : trialDays;
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

    private List<String> cleanFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            return new ArrayList<>();
        }

        return features.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}