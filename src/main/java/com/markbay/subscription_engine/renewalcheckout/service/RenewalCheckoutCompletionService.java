package com.markbay.subscription_engine.renewalcheckout.service;

import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;

public interface RenewalCheckoutCompletionService {

    void completeSuccessfulRenewalCheckout(
            String orderReference,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    );
}