package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.response.NombaBankAccountLookupResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaBankResult;

import java.util.List;

public interface NombaBankGateway {

    List<NombaBankResult> fetchBanks();

    NombaBankAccountLookupResult lookupBankAccount(
            String accountNumber,
            String bankCode
    );
}