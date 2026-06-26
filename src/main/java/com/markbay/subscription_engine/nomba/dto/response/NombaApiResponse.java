package com.markbay.subscription_engine.nomba.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NombaApiResponse<T>(

        @JsonAlias({"code", "statusCode"})
        String code,

        @JsonAlias({"description", "message"})
        String description,

        T data
) {
    public boolean isSuccessful() {
        return "00".equals(code);
    }
}