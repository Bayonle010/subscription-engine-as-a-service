package com.markbay.subscription_engine.payoutaccount.dto;

import com.markbay.subscription_engine.payoutaccount.entity.MerchantPayoutAccount;

import java.util.UUID;

public record MerchantPayoutAccountResponse(
        UUID id,
        String destinationType,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountName,
        String receiverAccountId,
        String status,
        boolean defaultAccount
) {
    public static MerchantPayoutAccountResponse from(MerchantPayoutAccount account) {
        return new MerchantPayoutAccountResponse(
                account.getId(),
                account.getDestinationType().name(),
                account.getBankCode(),
                account.getBankName(),
                maskAccountNumber(account.getAccountNumber()),
                account.getAccountName(),
                account.getReceiverAccountId(),
                account.getStatus().name(),
                account.isDefaultAccount()
        );
    }

    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }

        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}