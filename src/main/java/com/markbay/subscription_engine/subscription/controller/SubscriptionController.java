package com.markbay.subscription_engine.subscription.controller;

import com.markbay.subscription_engine.common.pagination.PaginationAdapters;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.subscription.dto.response.SubscriptionAnalyticsResponse;
import com.markbay.subscription_engine.subscription.dto.response.SubscriptionResponse;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.service.SubscriptionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionQueryService subscriptionQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSubscriptions(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize
    ) {
        Page<SubscriptionResponse> response =
                subscriptionQueryService.getSubscriptions(
                        status,
                        page,
                        pageSize
                );

        return ResponseEntity.ok(
                ResponseUtil.success(
                        00,
                        "Subscriptions fetched successfully",
                        null,
                        response.getContent(),
                        PaginationAdapters.toMeta(response)
        ));
    }

    @GetMapping("/{subscriptionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscriptionById(
            @PathVariable UUID subscriptionId
    ) {
        SubscriptionResponse response =
                subscriptionQueryService.getSubscriptionById(subscriptionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription fetched successfully",
                        response
                )
        );
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<SubscriptionAnalyticsResponse>> getSubscriptionAnalytics() {
        SubscriptionAnalyticsResponse response =
                subscriptionQueryService.getSubscriptionAnalytics();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Subscription analytics fetched successfully",
                        response
                )
        );
    }
}