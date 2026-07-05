package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaBankTransferRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaWalletTransferRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferResult;

public interface NombaTransferGateway {

    NombaTransferResult transferToBank(
            NombaBankTransferRequest request
    );

    NombaTransferResult transferToWallet(
            NombaWalletTransferRequest request
    );
}