package com.markbay.subscription_engine.planswitch.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.eventoutbox.service.EventOutboxService;
import com.markbay.subscription_engine.plan.entity.Plan;
import com.markbay.subscription_engine.plan.enums.PlanStatus;
import com.markbay.subscription_engine.plan.repository.PlanRepository;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchConfirmRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewRequest;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchPreviewResponse;
import com.markbay.subscription_engine.planswitch.dto.PlanSwitchResponse;
import com.markbay.subscription_engine.planswitch.dto.ProrationCalculation;
import com.markbay.subscription_engine.planswitch.entity.PlanSwitchRequest;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchMode;
import com.markbay.subscription_engine.planswitch.enums.PlanSwitchStatus;
import com.markbay.subscription_engine.planswitch.repository.PlanSwitchRequestRepository;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchBillingService;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchService;
import com.markbay.subscription_engine.planswitch.service.ProrationCalculationService;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSwitchServiceImpl implements PlanSwitchService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanSwitchRequestRepository planSwitchRequestRepository;
    private final ProrationCalculationService prorationCalculationService;
    private final PlanSwitchBillingService planSwitchBillingService;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional(readOnly = true)
    public PlanSwitchPreviewResponse previewPlanSwitch(
            UUID subscriptionId,
            PlanSwitchPreviewRequest request
    ) {
        Subscription subscription = findSubscription(subscriptionId);
        Plan newPlan = findActivePlan(request.newPlanId());

        validateCanSwitch(subscription, newPlan);

        ProrationCalculation calculation =
                prorationCalculationService.calculate(subscription, newPlan);

        return toPreviewResponse(subscription, newPlan, calculation);
    }

    @Override
    @Transactional
    public PlanSwitchResponse confirmPlanSwitch(
            UUID subscriptionId,
            PlanSwitchConfirmRequest request
    ) {
        Subscription subscription = findSubscriptionForUpdate(subscriptionId);
        Plan newPlan = findActivePlan(request.newPlanId());

        validateCanSwitch(subscription, newPlan);

        if (hasActivePlanSwitch(subscription.getId())) {
            throw new BadRequestException(
                    "Subscription already has a pending plan switch"
            );
        }

        ProrationCalculation calculation =
                prorationCalculationService.calculate(subscription, newPlan);

        PlanSwitchRequest planSwitchRequest =
                createPlanSwitchRequest(
                        subscription,
                        newPlan,
                        calculation
                );

        PlanSwitchRequest savedRequest =
                planSwitchRequestRepository.save(planSwitchRequest);

        if (calculation.mode() == PlanSwitchMode.PERIOD_END) {
            recordSubscriptionUpdatedEvent(
                    subscription,
                    savedRequest,
                    "PLAN_SWITCH_SCHEDULED",
                    "Plan switch scheduled for period end"
            );

            log.info(
                    "Plan switch scheduled. subscriptionId={}, oldPlanId={}, newPlanId={}, effectiveAt={}",
                    subscription.getId(),
                    subscription.getPlan().getId(),
                    newPlan.getId(),
                    savedRequest.getEffectiveAt()
            );

            return PlanSwitchResponse.from(savedRequest);
        }

        if (calculation.chargeAmount().signum() > 0) {
            planSwitchBillingService.chargeImmediatePlanSwitch(savedRequest);

            if (savedRequest.getStatus() == PlanSwitchStatus.FAILED) {
                return PlanSwitchResponse.from(savedRequest);
            }
        }

        applyPlanSwitch(
                savedRequest,
                "PLAN_SWITCH_APPLIED",
                "Plan switch applied immediately"
        );

        return PlanSwitchResponse.from(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanSwitchResponse getCurrentPlanSwitch(
            UUID subscriptionId
    ) {
        return planSwitchRequestRepository
                .findFirstBySubscription_IdAndStatusInOrderByCreatedAtDesc(
                        subscriptionId,
                        List.of(
                                PlanSwitchStatus.SCHEDULED,
                                PlanSwitchStatus.PAYMENT_PENDING
                        )
                )
                .map(PlanSwitchResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending plan switch found"
                ));
    }

    @Override
    @Transactional
    public PlanSwitchResponse cancelScheduledPlanSwitch(
            UUID subscriptionId
    ) {
        PlanSwitchRequest request =
                planSwitchRequestRepository
                        .findFirstBySubscription_IdAndStatusInOrderByCreatedAtDesc(
                                subscriptionId,
                                List.of(PlanSwitchStatus.SCHEDULED)
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No scheduled plan switch found"
                        ));

        request.setStatus(PlanSwitchStatus.CANCELLED);
        request.setCancelledAt(Instant.now());

        recordSubscriptionUpdatedEvent(
                request.getSubscription(),
                request,
                "PLAN_SWITCH_CANCELLED",
                "Scheduled plan switch cancelled"
        );

        log.info(
                "Scheduled plan switch cancelled. planSwitchRequestId={}, subscriptionId={}",
                request.getId(),
                request.getSubscription().getId()
        );

        return PlanSwitchResponse.from(request);
    }

    @Override
    @Transactional
    public void applyDueScheduledPlanSwitch(
            UUID planSwitchRequestId
    ) {
        PlanSwitchRequest request =
                planSwitchRequestRepository.findByIdForUpdate(planSwitchRequestId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Plan switch request not found"
                        ));

        if (request.getStatus() != PlanSwitchStatus.SCHEDULED) {
            return;
        }

        if (request.getEffectiveAt() == null
                || request.getEffectiveAt().isAfter(Instant.now())) {
            return;
        }

        applyPlanSwitch(
                request,
                "PLAN_SWITCH_APPLIED_AT_PERIOD_END",
                "Scheduled plan switch applied at period end"
        );
    }

    @Override
    @Transactional
    public void applyDueScheduledPlanSwitchForSubscription(
            UUID subscriptionId
    ) {
        planSwitchRequestRepository
                .findFirstBySubscription_IdAndStatusInOrderByCreatedAtDesc(
                        subscriptionId,
                        List.of(PlanSwitchStatus.SCHEDULED)
                )
                .ifPresent(request -> {
                    if (request.getEffectiveAt() != null
                            && !request.getEffectiveAt().isAfter(Instant.now())) {
                        applyPlanSwitch(
                                request,
                                "PLAN_SWITCH_APPLIED_AT_PERIOD_END",
                                "Scheduled plan switch applied at period end"
                        );
                    }
                });
    }

    private PlanSwitchRequest createPlanSwitchRequest(
            Subscription subscription,
            Plan newPlan,
            ProrationCalculation calculation
    ) {
        return PlanSwitchRequest.builder()
                .tenant(subscription.getTenant())
                .subscription(subscription)
                .oldPlan(subscription.getPlan())
                .newPlan(newPlan)
                .status(calculation.mode() == PlanSwitchMode.PERIOD_END
                        ? PlanSwitchStatus.SCHEDULED
                        : PlanSwitchStatus.PAYMENT_PENDING)
                .mode(calculation.mode())
                .direction(calculation.direction())
                .oldAmount(subscription.getAmount())
                .newAmount(newPlan.getAmount())
                .creditAmount(calculation.creditAmount())
                .chargeAmount(calculation.chargeAmount())
                .currency(subscription.getCurrency())
                .remainingSeconds(calculation.remainingSeconds())
                .totalPeriodSeconds(calculation.totalPeriodSeconds())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .effectiveAt(calculation.effectiveAt())
                .build();
    }

    private void applyPlanSwitch(
            PlanSwitchRequest request,
            String action,
            String message
    ) {
        Subscription subscription = request.getSubscription();
        Plan newPlan = request.getNewPlan();

        subscription.setPlan(newPlan);
        subscription.setAmount(newPlan.getAmount());
        subscription.setCurrency(newPlan.getCurrency());
        subscription.setBillingInterval(newPlan.getBillingInterval());
        subscription.setBillingIntervalCount(resolveBillingIntervalCount(newPlan));

        request.setStatus(PlanSwitchStatus.APPLIED);
        request.setAppliedAt(Instant.now());
        request.setFailureReason(null);

        recordSubscriptionUpdatedEvent(
                subscription,
                request,
                action,
                message
        );

        log.info(
                "Plan switch applied. planSwitchRequestId={}, subscriptionId={}, oldPlanId={}, newPlanId={}",
                request.getId(),
                subscription.getId(),
                request.getOldPlan().getId(),
                newPlan.getId()
        );
    }

    private void validateCanSwitch(
            Subscription subscription,
            Plan newPlan
    ) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.TRIALING) {
            throw new BadRequestException(
                    "Only active or trialing subscriptions can switch plans"
            );
        }

        if (subscription.isCancelAtPeriodEnd()) {
            throw new BadRequestException(
                    "Cannot switch plan for subscription scheduled for cancellation"
            );
        }

        if (!subscription.getTenant().getId().equals(newPlan.getTenant().getId())) {
            throw new BadRequestException(
                    "New plan does not belong to this tenant"
            );
        }

        if (newPlan.getStatus() != PlanStatus.ACTIVE) {
            throw new BadRequestException("New plan is not active");
        }

        if (subscription.getPlan().getId().equals(newPlan.getId())) {
            throw new BadRequestException("Subscription is already on this plan");
        }

        if (!subscription.getCurrency().equalsIgnoreCase(newPlan.getCurrency())) {
            throw new BadRequestException(
                    "Cannot switch plans with different currencies"
            );
        }
    }

    private Subscription findSubscription(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found"
                ));
    }

    private Subscription findSubscriptionForUpdate(UUID subscriptionId) {
        return subscriptionRepository.findByIdForRenewalUpdate(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found"
                ));
    }

    private Plan findActivePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plan not found"
                ));
    }

    private boolean hasActivePlanSwitch(UUID subscriptionId) {
        return planSwitchRequestRepository.existsBySubscription_IdAndStatusIn(
                subscriptionId,
                List.of(
                        PlanSwitchStatus.SCHEDULED,
                        PlanSwitchStatus.PAYMENT_PENDING
                )
        );
    }

    private PlanSwitchPreviewResponse toPreviewResponse(
            Subscription subscription,
            Plan newPlan,
            ProrationCalculation calculation
    ) {
        return new PlanSwitchPreviewResponse(
                subscription.getId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                newPlan.getId(),
                newPlan.getName(),
                calculation.mode(),
                calculation.direction(),
                subscription.getAmount(),
                newPlan.getAmount(),
                calculation.creditAmount(),
                calculation.chargeAmount(),
                subscription.getCurrency(),
                calculation.remainingSeconds(),
                calculation.totalPeriodSeconds(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                calculation.effectiveAt(),
                calculation.message()
        );
    }

    private void recordSubscriptionUpdatedEvent(
            Subscription subscription,
            PlanSwitchRequest request,
            String action,
            String message
    ) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("customerId", subscription.getCustomer().getId().toString());
        payload.put("customerEmail", subscription.getCustomer().getEmail());
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("subscriptionStatus", subscription.getStatus().name());
        payload.put("planSwitchRequestId", request.getId().toString());
        payload.put("oldPlanId", request.getOldPlan().getId().toString());
        payload.put("oldPlanName", request.getOldPlan().getName());
        payload.put("newPlanId", request.getNewPlan().getId().toString());
        payload.put("newPlanName", request.getNewPlan().getName());
        payload.put("mode", request.getMode().name());
        payload.put("direction", request.getDirection().name());
        payload.put("creditAmount", request.getCreditAmount().toPlainString());
        payload.put("chargeAmount", request.getChargeAmount().toPlainString());
        payload.put("currency", request.getCurrency());
        payload.put("effectiveAt", safeInstant(request.getEffectiveAt()));
        payload.put("action", action);
        payload.put("message", message);

        eventOutboxService.recordEvent(
                CreateEventOutboxCommand.builder()
                        .tenant(subscription.getTenant())
                        .eventType(EventOutboxType.SUBSCRIPTION_UPDATED)
                        .eventReference(
                                "subscription.updated:"
                                        + action
                                        + ":"
                                        + request.getId()
                        )
                        .aggregateType("subscription")
                        .aggregateId(subscription.getId().toString())
                        .payload(payload)
                        .build()
        );
    }

    private int resolveBillingIntervalCount(Plan plan) {
        if (plan.getBillingIntervalCount() == null
                || plan.getBillingIntervalCount() <= 0) {
            return 1;
        }

        return plan.getBillingIntervalCount();
    }

    private String safeInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}