package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;

public interface PaymentRescueCompletionService {

    void completeSuccessfulRescuePayment(
            String orderReference,
            NombaVerifiedTransactionResult verifiedTransaction,
            NombaWebhookPaymentData paymentData
    );

    void markRescuePaymentFailed(
            String orderReference,
            String reason
    );
}