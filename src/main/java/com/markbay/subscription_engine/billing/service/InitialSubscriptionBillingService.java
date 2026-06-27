package com.markbay.subscription_engine.billing.service;

import com.markbay.subscription_engine.billing.dto.BillingRecordResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;

public interface InitialSubscriptionBillingService {

    BillingRecordResult recordInitialSubscriptionPayment(
            Subscription subscription,
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction
    );
}