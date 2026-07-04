package com.markbay.subscription_engine.payoutaccount.service;

import com.markbay.subscription_engine.payoutaccount.dto.MerchantPayoutAccountResponse;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupRequest;
import com.markbay.subscription_engine.payoutaccount.dto.BankAccountLookupResponse;
import com.markbay.subscription_engine.payoutaccount.dto.CreatePayoutAccountRequest;

import java.util.List;
import java.util.UUID;

public interface MerchantPayoutAccountService {

    BankAccountLookupResponse lookupBankAccount(
            BankAccountLookupRequest request
    );

    MerchantPayoutAccountResponse createPayoutAccount(
            CreatePayoutAccountRequest request
    );

    List<MerchantPayoutAccountResponse> listPayoutAccounts();

    MerchantPayoutAccountResponse disablePayoutAccount(
            UUID payoutAccountId
    );
}