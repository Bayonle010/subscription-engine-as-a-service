package com.markbay.subscription_engine.payoutaccount.dto;

public record BankAccountLookupResponse(
        String accountNumber,
        String accountName,
        String bankCode,
        String bankName
) {
}