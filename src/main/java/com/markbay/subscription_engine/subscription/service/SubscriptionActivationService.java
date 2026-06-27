package com.markbay.subscription_engine.subscription.service;

import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscriptioncheckout.entity.SubscriptionCheckoutSession;

public interface SubscriptionActivationService {

    Subscription activateFromSuccessfulCheckout(
            SubscriptionCheckoutSession checkoutSession,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    );
}