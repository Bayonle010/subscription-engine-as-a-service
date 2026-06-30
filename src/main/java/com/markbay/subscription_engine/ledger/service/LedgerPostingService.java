package com.markbay.subscription_engine.ledger.service;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.subscription.entity.Subscription;

public interface LedgerPostingService {

    LedgerPostingResult postInitialSubscriptionPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    );

    LedgerPostingResult postRenewalSubscriptionPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    );

}