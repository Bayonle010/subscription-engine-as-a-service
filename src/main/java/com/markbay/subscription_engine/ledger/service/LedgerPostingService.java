package com.markbay.subscription_engine.ledger.service;

import com.markbay.subscription_engine.billing.dto.BillingFeeResult;
import com.markbay.subscription_engine.ledger.dto.LedgerPostingResult;
import com.markbay.subscription_engine.payment.entity.Payment;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.tenant.entity.Tenant;

import java.math.BigDecimal;
import java.util.UUID;

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

    LedgerPostingResult postProrationPayment(
            Subscription subscription,
            Payment payment,
            BillingFeeResult feeResult
    );

    LedgerPostingResult holdMerchantWithdrawal(
            Tenant tenant,
            UUID withdrawalId,
            BigDecimal amount,
            String currency
    );

    LedgerPostingResult settleMerchantWithdrawal(
            Tenant tenant,
            UUID withdrawalId,
            BigDecimal amount,
            String currency
    );

    LedgerPostingResult releaseMerchantWithdrawalHold(
            Tenant tenant,
            UUID withdrawalId,
            BigDecimal amount,
            String currency
    );

}