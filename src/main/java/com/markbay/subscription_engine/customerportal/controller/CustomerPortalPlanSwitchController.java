package com.markbay.subscription_engine.customerportal.controller;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalPlanSwitchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer-portal/sessions/{token}/plan-switch")
@RequiredArgsConstructor
public class CustomerPortalPlanSwitchController {

    private final CustomerPortalPlanSwitchService customerPortalPlanSwitchService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<PlanSwitchPreviewResponse>> previewPlanSwitch(
            @PathVariable String token,
            @Valid @RequestBody PlanSwitchPreviewRequest request
    ) {
        PlanSwitchPreviewResponse response =
                customerPortalPlanSwitchService.previewPlanSwitch(token, request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Plan switch preview generated successfully",
                        response
                )
        );
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> confirmPlanSwitch(
            @PathVariable String token,
            @Valid @RequestBody PlanSwitchConfirmRequest request
    ) {
        PlanSwitchResponse response =
                customerPortalPlanSwitchService.confirmPlanSwitch(token, request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Plan switch confirmed successfully",
                        response
                )
        );
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> getCurrentPlanSwitch(
            @PathVariable String token
    ) {
        PlanSwitchResponse response =
                customerPortalPlanSwitchService.getCurrentPlanSwitch(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Current plan switch retrieved successfully",
                        response
                )
        );
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<PlanSwitchResponse>> cancelPlanSwitch(
            @PathVariable String token
    ) {
        PlanSwitchResponse response =
                customerPortalPlanSwitchService.cancelPlanSwitch(token);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "Scheduled plan switch cancelled successfully",
                        response
                )
        );
    }
}