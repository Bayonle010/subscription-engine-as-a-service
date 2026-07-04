package com.markbay.subscription_engine.nomba.dto.request;

import java.math.BigDecimal;

public record NombaBankTransferRequest(
        BigDecimal amount,
        String accountNumber,
        String accountName,
        String bankCode,
        String merchantTxRef,
        String senderName,
        String narration
) {
}