package com.markbay.subscription_engine.financial.listener;

import com.markbay.subscription_engine.financial.service.TenantFinancialAccountService;
import com.markbay.subscription_engine.tenant.event.TenantRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class TenantFinancialSetupListener {

    private final TenantFinancialAccountService financialAccountService;

    @Async("financialSetupExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTenantRegistered(TenantRegisteredEvent event) {
        try {
            log.info(
                    "Starting async tenant financial setup. tenantId={}",
                    event.tenantId()
            );

            financialAccountService.setupFinancialAccountForTenant(event.tenantId());

            log.info(
                    "Async tenant financial setup completed. tenantId={}",
                    event.tenantId()
            );

        } catch (Exception exception) {
            log.error(
                    "Async tenant financial setup failed. tenantId={}, reason={}",
                    event.tenantId(),
                    exception.getMessage(),
                    exception
            );
        }
    }
}