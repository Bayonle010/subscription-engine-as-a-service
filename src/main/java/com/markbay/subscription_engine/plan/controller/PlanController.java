package com.markbay.subscription_engine.plan.controller;

import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.plan.dto.CreatePlanRequest;
import com.markbay.subscription_engine.plan.dto.PlanResponse;
import com.markbay.subscription_engine.plan.dto.UpdatePlanRequest;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import com.markbay.subscription_engine.plan.service.PlanService;
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
public class PlanController {

    private final PlanService planService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PostMapping("/api/v1/products/{productId}/plans")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @PathVariable UUID productId,
            @Valid @RequestBody CreatePlanRequest request
    ) {
        PlanResponse response = planService.createPlan(productId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseUtil.success(
                "Plan created successfully", response
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping("/api/v1/products/{productId}/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listProductPlans(
            @PathVariable UUID productId,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) PlanStatus status
    ) {
        Page<PlanResponse> response = planService.listProductPlans(
                productId,
                page,
                pageSize,
                status
        );

        return ResponseEntity.ok().body(ResponseUtil.success(
                0,
                "Product plans retrieved successfully",
                null,
                response.getContent(),
                PaginationAdapters.toMeta(response))
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping("/api/v1/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>>listPlans(
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) PlanStatus status
    ) {
        Page<PlanResponse> response = planService.listPlans(
                page,
                pageSize,
                status
        );

        return ResponseEntity.ok().body(ResponseUtil.success(
                0,
                "Plans retrieved successfully",
                null,
                response.getContent(),
                PaginationAdapters.toMeta(response)
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT', 'API_CLIENT')")
    @GetMapping("/api/v1/plans/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlan(
            @PathVariable UUID planId
    ) {
        PlanResponse response = planService.getPlan(planId);

        return ResponseEntity.ok().body(ResponseUtil.success(
                0,
                "Plan retrieved successfully",
                null,
                response,
                null
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PatchMapping("/api/v1/plans/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanRequest request
    ) {
        PlanResponse response = planService.updatePlan(planId, request);

        return ResponseEntity.ok().body(ResponseUtil.success(
                0,
                "Plan updated successfully",
                null,
                response,
                null
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'API_CLIENT')")
    @PatchMapping("/api/v1/plans/{planId}/archive")
    public ResponseEntity<ApiResponse<PlanResponse>> archivePlan(
            @PathVariable UUID planId
    ) {
        PlanResponse response = planService.archivePlan(planId);

        return ResponseEntity.ok().body(ResponseUtil.success(
                0,
                "Plan archived successfully",
                null,
                response,
                null
        ));
    }
}