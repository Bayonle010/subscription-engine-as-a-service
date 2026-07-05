package com.markbay.subscription_engine.merchantwithdrawal.service;

import com.markbay.subscription_engine.merchantwithdrawal.dto.NombaPayoutWebhookData;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferStatusResult;

import java.util.UUID;

public interface MerchantWithdrawalVerificationService {

    void applyInitialTransferResult(
            UUID withdrawalId,
            NombaTransferResult transferResult
    );

    void handlePayoutWebhook(
            NombaPayoutWebhookData payoutWebhookData
    );

    void reconcileProcessingWithdrawal(
            UUID withdrawalId
    );

    void applyTransferStatusResult(
            UUID withdrawalId,
            NombaTransferStatusResult statusResult
    );
}