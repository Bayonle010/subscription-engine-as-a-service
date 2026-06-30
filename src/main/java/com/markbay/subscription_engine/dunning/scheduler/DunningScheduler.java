package com.markbay.subscription_engine.dunning.scheduler;

import com.markbay.subscription_engine.dunning.service.DunningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DunningScheduler {

    private final DunningService dunningService;

    @Value("${dunning.processor.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dunning.processor.fixed-delay-ms:60000}")
    public void processDunningCases() {
        List<UUID> dueCaseIds = dunningService.findDueDunningCaseIds(batchSize);

        if (dueCaseIds.isEmpty()) {
            return;
        }

        log.info("Processing due dunning cases. count={}", dueCaseIds.size());

        for (UUID caseId : dueCaseIds) {
            try {
                dunningService.processDunningCase(caseId);
            } catch (Exception exception) {
                log.error(
                        "Dunning processing failed. dunningCaseId={}, reason={}",
                        caseId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}