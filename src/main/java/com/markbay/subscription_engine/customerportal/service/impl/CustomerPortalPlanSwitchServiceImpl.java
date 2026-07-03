package com.markbay.subscription_engine.customerportal.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.customerportal.entity.CustomerPortalSession;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionPurpose;
import com.markbay.subscription_engine.customerportal.enums.CustomerPortalSessionStatus;
import com.markbay.subscription_engine.customerportal.repository.CustomerPortalSessionRepository;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalPlanSwitchService;
import com.markbay.subscription_engine.customerportal.service.CustomerPortalTokenService;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPortalPlanSwitchServiceImpl
        implements CustomerPortalPlanSwitchService {

    private final CustomerPortalTokenService tokenService;
    private final CustomerPortalSessionRepository portalSessionRepository;
    private final PlanSwitchService planSwitchService;

    @Override
    public PlanSwitchPreviewResponse previewPlanSwitch(
            String rawToken,
            PlanSwitchPreviewRequest request
    ) {
        CustomerPortalSession session = requireManagementSession(rawToken);

        UUID subscriptionId = session.getSubscription().getId();

        return planSwitchService.previewPlanSwitch(
                subscriptionId,
                request
        );
    }

    @Override
    public PlanSwitchResponse confirmPlanSwitch(
            String rawToken,
            PlanSwitchConfirmRequest request
    ) {
        CustomerPortalSession session = requireManagementSession(rawToken);

        UUID subscriptionId = session.getSubscription().getId();

        return planSwitchService.confirmPlanSwitch(
                subscriptionId,
                request
        );
    }

    @Override
    public PlanSwitchResponse getCurrentPlanSwitch(
            String rawToken
    ) {
        CustomerPortalSession session = requireManagementSession(rawToken);

        UUID subscriptionId = session.getSubscription().getId();

        return planSwitchService.getCurrentPlanSwitch(subscriptionId);
    }

    @Override
    public PlanSwitchResponse cancelPlanSwitch(
            String rawToken
    ) {
        CustomerPortalSession session = requireManagementSession(rawToken);

        UUID subscriptionId = session.getSubscription().getId();

        return planSwitchService.cancelScheduledPlanSwitch(subscriptionId);
    }

    private CustomerPortalSession requireManagementSession(String rawToken) {
        if (!hasText(rawToken)) {
            throw new BadRequestException("Portal token is required");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        CustomerPortalSession session =
                portalSessionRepository.findByTokenHash(tokenHash)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Customer portal session not found"
                        ));

        if (session.getStatus() != CustomerPortalSessionStatus.ACTIVE) {
            throw new BadRequestException("Customer portal session is not active");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(CustomerPortalSessionStatus.EXPIRED);
            throw new BadRequestException("Customer portal session has expired");
        }

        if (session.getPurpose() != CustomerPortalSessionPurpose.MANAGE_SUBSCRIPTION) {
            throw new BadRequestException(
                    "This customer portal session cannot be used to switch plans"
            );
        }

        if (session.getSubscription() == null) {
            throw new BadRequestException("Customer portal session has no subscription");
        }

        return session;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}