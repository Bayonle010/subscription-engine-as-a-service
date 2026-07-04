package com.markbay.subscription_engine.merchantwithdrawal.service;

import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateBankMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateNombaWalletMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.MerchantWithdrawalResponse;

import java.util.List;
import java.util.UUID;

public interface MerchantWithdrawalService {

    MerchantWithdrawalResponse requestBankWithdrawal(
            String idempotencyKey,
            CreateBankMerchantWithdrawalRequest request
    );

    MerchantWithdrawalResponse requestNombaWalletWithdrawal(
            String idempotencyKey,
            CreateNombaWalletMerchantWithdrawalRequest request
    );

    List<MerchantWithdrawalResponse> listWithdrawals();

    MerchantWithdrawalResponse getWithdrawal(
            UUID withdrawalId
    );

    void dispatchWithdrawal(
            UUID withdrawalId
    );
}