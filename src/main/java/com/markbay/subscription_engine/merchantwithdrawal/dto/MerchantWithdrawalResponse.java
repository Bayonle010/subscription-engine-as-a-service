package com.markbay.subscription_engine.merchantwithdrawal.dto;

import com.markbay.subscription_engine.merchantwithdrawal.entity.MerchantWithdrawal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantWithdrawalResponse(
        UUID id,
        String status,
        String destinationType,
        BigDecimal amount,
        String currency,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountName,
        String receiverAccountId,
        String merchantTxRef,
        String providerTransferId,
        String providerStatus,
        String failureReason,
        Instant createdAt,
        Instant succeededAt,
        Instant failedAt
) {
    public static MerchantWithdrawalResponse from(MerchantWithdrawal withdrawal) {
        return new MerchantWithdrawalResponse(
                withdrawal.getId(),
                withdrawal.getStatus().name(),
                withdrawal.getDestinationType().name(),
                withdrawal.getAmount(),
                withdrawal.getCurrency(),
                withdrawal.getBankCode(),
                withdrawal.getBankName(),
                maskAccountNumber(withdrawal.getAccountNumber()),
                withdrawal.getAccountName(),
                withdrawal.getReceiverAccountId(),
                withdrawal.getMerchantTxRef(),
                withdrawal.getProviderTransferId(),
                withdrawal.getProviderStatus(),
                withdrawal.getFailureReason(),
                withdrawal.getCreatedAt(),
                withdrawal.getSucceededAt(),
                withdrawal.getFailedAt()
        );
    }

    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }

        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}