package com.markbay.subscription_engine.merchantwithdrawal.scheduler;

import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.merchantwithdrawal.repository.MerchantWithdrawalRepository;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalService;
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
public class MerchantWithdrawalDispatchScheduler {

    private final MerchantWithdrawalRepository withdrawalRepository;
    private final MerchantWithdrawalService withdrawalService;

    @Value("${merchant-withdrawal.dispatch.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${merchant-withdrawal.dispatch.fixed-delay-ms:30000}")
    public void dispatchDueWithdrawals() {
        List<UUID> withdrawalIds =
                withdrawalRepository.findDueWithdrawalIds(
                        List.of(
                                MerchantWithdrawalStatus.HELD,
                                MerchantWithdrawalStatus.PROCESSING
                        ),
                        Instant.now(),
                        PageRequest.of(0, batchSize)
                );

        if (withdrawalIds.isEmpty()) {
            return;
        }

        log.info(
                "Dispatching merchant withdrawals. count={}",
                withdrawalIds.size()
        );

        for (UUID withdrawalId : withdrawalIds) {
            try {
                withdrawalService.dispatchWithdrawal(withdrawalId);
            } catch (Exception exception) {
                log.error(
                        "Merchant withdrawal dispatch failed. withdrawalId={}, reason={}",
                        withdrawalId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}