package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardChargeRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaTokenizedCardChargeResult;

public interface NombaTokenizedCardChargeGateway {

    NombaTokenizedCardChargeResult chargeTokenizedCard(
            NombaTokenizedCardChargeRequest request
    );
}