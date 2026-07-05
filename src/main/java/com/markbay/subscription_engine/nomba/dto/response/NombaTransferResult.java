package com.markbay.subscription_engine.nomba.dto.response;

import java.math.BigDecimal;

public record NombaTransferResult(
        boolean accepted,
        boolean successful,
        boolean pending,
        String transferId,
        String status,
        String merchantTxRef,
        BigDecimal amount,
        BigDecimal fee,
        String rawResponse
) {
}