package com.markbay.subscription_engine.merchantwithdrawal.service;

import com.markbay.subscription_engine.merchantwithdrawal.dto.CreateMerchantWithdrawalRequest;
import com.markbay.subscription_engine.merchantwithdrawal.dto.MerchantWithdrawalResponse;

import java.util.List;
import java.util.UUID;

public interface MerchantWithdrawalService {

    MerchantWithdrawalResponse requestWithdrawal(
            String idempotencyKey,
            CreateMerchantWithdrawalRequest request
    );

    List<MerchantWithdrawalResponse> listWithdrawals();

    MerchantWithdrawalResponse getWithdrawal(
            UUID withdrawalId
    );

    MerchantWithdrawalResponse retryWithdrawal(
            UUID withdrawalId
    );
}