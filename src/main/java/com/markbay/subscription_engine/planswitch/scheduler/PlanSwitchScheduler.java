package com.markbay.subscription_engine.planswitch.scheduler;

import com.markbay.subscription_engine.planswitch.enums.PlanSwitchStatus;
import com.markbay.subscription_engine.planswitch.repository.PlanSwitchRequestRepository;
import com.markbay.subscription_engine.planswitch.service.PlanSwitchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSwitchScheduler {

    private final PlanSwitchRequestRepository planSwitchRequestRepository;
    private final PlanSwitchService planSwitchService;

    @Value("${plan-switch.processor.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${plan-switch.processor.fixed-delay-ms:60000}")
    public void processScheduledPlanSwitches() {
        List<UUID> dueRequestIds =
                planSwitchRequestRepository.findDueScheduledPlanSwitchIds(
                        PlanSwitchStatus.SCHEDULED,
                        Instant.now(),
                        PageRequest.of(0, batchSize)
                );

        if (dueRequestIds.isEmpty()) {
            return;
        }

        log.info(
                "Processing scheduled plan switches. count={}",
                dueRequestIds.size()
        );

        for (UUID requestId : dueRequestIds) {
            try {
                planSwitchService.applyDueScheduledPlanSwitch(requestId);
            } catch (Exception exception) {
                log.error(
                        "Scheduled plan switch processing failed. requestId={}, reason={}",
                        requestId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}