package com.markbay.subscription_engine.nomba.dto.response;

import java.math.BigDecimal;

public record NombaTransferStatusResult(
        boolean found,
        boolean successful,
        boolean pending,
        boolean reversed,
        boolean failed,
        String transferId,
        String merchantTxRef,
        String status,
        BigDecimal amount,
        BigDecimal fee,
        String rawResponse
) {
}