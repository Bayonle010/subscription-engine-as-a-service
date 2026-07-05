package com.markbay.subscription_engine.nomba.dto.request;

import java.math.BigDecimal;

public record NombaWalletTransferRequest(
        BigDecimal amount,
        String receiverAccountId,
        String merchantTxRef,
        String narration
) {
}