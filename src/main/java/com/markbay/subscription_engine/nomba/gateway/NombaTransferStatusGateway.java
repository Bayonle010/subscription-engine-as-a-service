package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.response.NombaTransferStatusResult;

public interface NombaTransferStatusGateway {

    NombaTransferStatusResult requerySubAccountTransfer(
            String transactionRef
    );
}