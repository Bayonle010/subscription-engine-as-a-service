package com.markbay.subscription_engine.merchantwithdrawal.scheduler;

import com.markbay.subscription_engine.merchantwithdrawal.enums.MerchantWithdrawalStatus;
import com.markbay.subscription_engine.merchantwithdrawal.repository.MerchantWithdrawalRepository;
import com.markbay.subscription_engine.merchantwithdrawal.service.MerchantWithdrawalVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantWithdrawalReconciliationScheduler {

    private final MerchantWithdrawalRepository withdrawalRepository;
    private final MerchantWithdrawalVerificationService verificationService;

    @Value("${merchant-withdrawal.reconciliation.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${merchant-withdrawal.reconciliation.fixed-delay-ms:120000}")
    public void reconcileProcessingWithdrawals() {
        List<UUID> withdrawalIds =
                withdrawalRepository.findWithdrawalIdsByStatus(
                        MerchantWithdrawalStatus.PROCESSING,
                        PageRequest.of(0, batchSize)
                );

        if (withdrawalIds.isEmpty()) {
            return;
        }

        log.info(
                "Reconciling processing merchant withdrawals. count={}",
                withdrawalIds.size()
        );

        for (UUID withdrawalId : withdrawalIds) {
            try {
                verificationService.reconcileProcessingWithdrawal(withdrawalId);
            } catch (Exception exception) {
                log.error(
                        "Merchant withdrawal reconciliation failed. withdrawalId={}, reason={}",
                        withdrawalId,
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}