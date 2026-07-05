package com.markbay.subscription_engine.merchantwithdrawal.dto;


import java.math.BigDecimal;

public record NombaPayoutWebhookData(
        String eventType,
        String merchantTxRef,
        String transferId,
        String providerStatus,
        BigDecimal amount,
        String rawResponse
) {
}