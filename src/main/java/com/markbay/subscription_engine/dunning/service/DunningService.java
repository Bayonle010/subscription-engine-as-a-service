package com.markbay.subscription_engine.dunning.service;

import com.markbay.subscription_engine.invoice.entity.Invoice;
import com.markbay.subscription_engine.paymentattempt.entity.PaymentAttempt;
import com.markbay.subscription_engine.subscription.entity.Subscription;

import java.util.List;
import java.util.UUID;

public interface DunningService {

    void openCaseForFailedRenewal(
            Subscription subscription,
            Invoice invoice,
            PaymentAttempt failedAttempt,
            String billingReference,
            String failureReason
    );

    List<UUID> findDueDunningCaseIds(int batchSize);

    void processDunningCase(UUID dunningCaseId);
}