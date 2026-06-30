package com.markbay.subscription_engine.nomba.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaTokenizedCardChargeRequest(
        String tokenKey,
        NombaTokenizedCardOrder order
) {
}