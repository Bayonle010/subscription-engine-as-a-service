package com.markbay.subscription_engine.planswitch.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions/{subscriptionId}/plan-switch")
@RequiredArgsConstructor
public class PlanSwitchController {

    private final PlanSwitchService planSwitchService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<PlanSwitchPreviewResponse>> previewPlanSwitch(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody PlanSwitchPreviewRequest request
    ) {
        PlanSwitchPreviewResponse response =
                planSwitchService.previewPlanSwitch(subscriptionId, request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Plan switch preview generated successfully",
                        response
                )
        );
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> confirmPlanSwitch(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody PlanSwitchConfirmRequest request
    ) {
        PlanSwitchResponse response =
                planSwitchService.confirmPlanSwitch(subscriptionId, request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Plan switch confirmed successfully",
                        response
                )
        );
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','SUPPORT','API_CLIENT')")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> getCurrentPlanSwitch(
            @PathVariable UUID subscriptionId
    ) {
        PlanSwitchResponse response =
                planSwitchService.getCurrentPlanSwitch(subscriptionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Current plan switch retrieved successfully",
                        response
                )
        );
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','DEVELOPER','API_CLIENT')")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> cancelScheduledPlanSwitch(
            @PathVariable UUID subscriptionId
    ) {
        PlanSwitchResponse response =
                planSwitchService.cancelScheduledPlanSwitch(subscriptionId);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Scheduled plan switch cancelled successfully",
                        response
                )
        );
    }
}